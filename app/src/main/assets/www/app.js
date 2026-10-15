// Google Drive Replica - LocalShare Client
let currentFolderId = 'root';
let currentFiles = [];
let currentFolders = [];
let allFiles = []; // for search filtering
let allFolders = [];
let isListView = false;

// Helpers
function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatDate(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function getFileIcon(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();

  if (m.startsWith('image/') || /\.(png|jpg|jpeg|gif|webp|svg|bmp)$/.test(n)) return '🖼️';
  if (m.startsWith('video/') || /\.(mp4|mkv|webm|avi|mov)$/.test(n)) return '🎬';
  if (m.startsWith('audio/') || /\.(mp3|wav|ogg|m4a|aac|flac)$/.test(n)) return '🎵';
  if (m.includes('pdf') || n.endsWith('.pdf')) return '📕';
  if (/\.(zip|tar|gz|rar|7z)$/.test(n)) return '📦';
  if (/\.(txt|md|log)$/.test(n)) return '📝';
  if (/\.(json|js|html|css|java|py|cpp|c|xml|sh)$/.test(n)) return '📄';
  if (/\.(doc|docx)$/.test(n)) return '📘';
  if (/\.(xls|xlsx|csv)$/.test(n)) return '📊';
  if (/\.(ppt|pptx)$/.test(n)) return '📙';
  return '📄';
}

function isImage(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();
  return m.startsWith('image/') || /\.(png|jpg|jpeg|gif|webp|svg|bmp)$/.test(n);
}

function isVideo(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();
  return m.startsWith('video/') || /\.(mp4|webm|mkv|mov)$/.test(n);
}

function isAudio(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();
  return m.startsWith('audio/') || /\.(mp3|wav|ogg|m4a|aac)$/.test(n);
}

function isPdf(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();
  return m.includes('pdf') || n.endsWith('.pdf');
}

function isText(name, mime) {
  const n = (name || '').toLowerCase();
  const m = (mime || '').toLowerCase();
  return m.startsWith('text/') || /\.(txt|json|js|html|css|java|py|xml|md|sh|csv)$/.test(n);
}

// API Fetch wrapper
async function api(path, opts = {}) {
  const res = await fetch(path, opts);
  if (res.status === 401) {
    window.location.href = '/login';
    throw new Error('unauth');
  }
  if (res.status === 403) {
    try { await fetch('/api/request-access', { method: 'POST' }); } catch (ignore) {}
    showApprovalNotice(true);
    throw new Error('forbidden');
  }
  if (!res.ok) {
    throw new Error(await res.text());
  }
  return res;
}

function showApprovalNotice(show) {
  const el = document.getElementById('approvalNotice');
  if (show) {
    el.classList.remove('hidden');
    document.getElementById('foldersSection').classList.add('hidden');
    document.getElementById('filesSection').classList.add('hidden');
    document.getElementById('emptyState').classList.add('hidden');
  } else {
    el.classList.add('hidden');
  }
}

// Navigation / Route parsing
function parseRoute() {
  setSidebarOpen(false);
  const hash = window.location.hash || '#/folders/root';

  // File preview route: #/file/{id}/view
  const fileMatch = hash.match(/^#\/file\/([^/]+)\/view$/);
  if (fileMatch) {
    const fileId = fileMatch[1];
    openFilePreviewById(fileId);
    return;
  }

  // Folder route: #/folders/{id} or #/drive/my-drive
  const folderMatch = hash.match(/^#\/folders\/([^/]+)$/);
  if (folderMatch) {
    const folderId = folderMatch[1];
    closePreviewModal(false);
    loadFolder(folderId);
    return;
  }

  if (hash === '#/drive/my-drive' || hash === '' || hash === '#') {
    closePreviewModal(false);
    loadFolder('root');
    return;
  }

  // Fallback
  loadFolder('root');
}

// Load folder contents
async function loadFolder(folderId) {
  currentFolderId = folderId;
  const q = (folderId && folderId !== 'root') ? '?folder=' + encodeURIComponent(folderId) : '';

  try {
    const res = await api('/api/files' + q);
    const data = await res.json();
    showApprovalNotice(false);

    // Update breadcrumbs
    renderBreadcrumbs(data.breadcrumbs || [{ id: 'root', name: 'My Drive' }]);

    // Separate folders and files
    const items = data.files || [];
    currentFolders = items.filter(i => i.isFolder);
    currentFiles = items.filter(i => !i.isFolder);
    allFolders = [...currentFolders];
    allFiles = [...currentFiles];

    renderContent();
  } catch (e) {
    console.error('Error loading folder:', e);
  }
}

// Render breadcrumbs
function renderBreadcrumbs(crumbs) {
  const container = document.getElementById('breadcrumbsContainer');
  container.innerHTML = '';

  crumbs.forEach((c, idx) => {
    const isLast = idx === crumbs.length - 1;
    const a = document.createElement('a');
    a.className = 'bc-chip' + (isLast ? ' active' : '');
    a.textContent = c.name;
    a.href = '#/folders/' + (c.id === 'root' ? 'root' : c.id);
    container.appendChild(a);

    if (!isLast) {
      const sep = document.createElement('span');
      sep.className = 'bc-separator';
      sep.innerHTML = '›';
      container.appendChild(sep);
    }
  });
}

// Render content (Folders & Files)
function renderContent() {
  const foldersSection = document.getElementById('foldersSection');
  const filesSection = document.getElementById('filesSection');
  const emptyState = document.getElementById('emptyState');
  const foldersGrid = document.getElementById('foldersGrid');
  const filesGrid = document.getElementById('filesGrid');
  const filesTableBody = document.getElementById('filesTableBody');

  foldersGrid.innerHTML = '';
  filesGrid.innerHTML = '';
  filesTableBody.innerHTML = '';

  const hasFolders = currentFolders.length > 0;
  const hasFiles = currentFiles.length > 0;

  if (!hasFolders && !hasFiles) {
    foldersSection.classList.add('hidden');
    filesSection.classList.add('hidden');
    emptyState.classList.remove('hidden');
    return;
  }

  emptyState.classList.add('hidden');

  // Render Folders
  if (hasFolders) {
    foldersSection.classList.remove('hidden');
    currentFolders.forEach(folder => {
      const card = document.createElement('div');
      card.className = 'folder-card';
      card.title = folder.name;
      card.innerHTML = `
        <svg class="folder-card-icon" viewBox="0 0 24 24">
          <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
        </svg>
        <span class="folder-card-name">${escapeHtml(folder.name)}</span>
      `;
      card.onclick = () => {
        window.location.hash = '#/folders/' + folder.id;
      };
      foldersGrid.appendChild(card);
    });
  } else {
    foldersSection.classList.add('hidden');
  }

  // Render Files
  if (hasFiles) {
    filesSection.classList.remove('hidden');

    if (isListView) {
      document.getElementById('filesGrid').classList.add('hidden');
      document.getElementById('filesTableWrap').classList.remove('hidden');

      currentFiles.forEach(f => {
        const tr = document.createElement('tr');
        const icon = getFileIcon(f.name, f.mime);
        tr.innerHTML = `
          <td>
            <div class="td-file-name">
              <span>${icon}</span>
              <span title="${escapeHtml(f.name)}">${escapeHtml(f.name)}</span>
            </div>
          </td>
          <td>${formatBytes(f.size)}</td>
          <td>${escapeHtml((f.mime || 'File').split('/')[1] || 'File')}</td>
          <td>${formatDate(f.updatedAt)}</td>
          <td>
            <div class="table-actions">
              <button class="btn-row-action btn-preview" title="Open preview">Open</button>
              <button class="btn-row-action btn-download-row" title="Download in chunks">Download</button>
            </div>
          </td>
        `;
        tr.querySelector('.btn-preview').onclick = (e) => {
          e.stopPropagation();
          openFilePreview(f);
        };
        tr.querySelector('.btn-download-row').onclick = (e) => {
          e.stopPropagation();
          startChunkDownload(f);
        };
        tr.onclick = () => openFilePreview(f);
        filesTableBody.appendChild(tr);
      });

    } else {
      document.getElementById('filesGrid').classList.remove('hidden');
      document.getElementById('filesTableWrap').classList.add('hidden');

      currentFiles.forEach(f => {
        const card = document.createElement('div');
        card.className = 'file-card';
        card.title = f.name;

        const icon = getFileIcon(f.name, f.mime);
        const imgThumb = isImage(f.name, f.mime)
          ? `<img class="file-thumb-img" src="/api/view/${f.id}" alt="${escapeHtml(f.name)}" loading="lazy"/>`
          : `<span class="file-icon-large">${icon}</span>`;

        card.innerHTML = `
          <div class="file-card-preview">
            ${imgThumb}
          </div>
          <div class="file-card-info">
            <div class="file-card-header">
              <span class="file-type-badge">${icon}</span>
              <span class="file-card-name">${escapeHtml(f.name)}</span>
            </div>
            <div class="file-card-footer">
              <span class="file-card-size">${formatBytes(f.size)}</span>
              <button class="file-card-download-btn btn-card-dl" title="Download in chunks" onclick="event.stopPropagation()">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                  <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>
                </svg>
              </button>
            </div>
          </div>
        `;

        card.querySelector('.btn-card-dl').onclick = (e) => {
          e.stopPropagation();
          startChunkDownload(f);
        };
        card.onclick = () => openFilePreview(f);
        filesGrid.appendChild(card);
      });
    }
  } else {
    filesSection.classList.add('hidden');
  }
}

// Google Drive File Preview Modal
let activePreviewFile = null;

function openFilePreview(f) {
  window.location.hash = '#/file/' + f.id + '/view';
  showPreviewModalWithFile(f);
}

async function openFilePreviewById(fileId) {
  let f = currentFiles.find(item => item.id === fileId);
  if (!f) {
    try {
      const res = await api('/api/files/' + fileId + '/meta');
      f = await res.json();
    } catch (e) {
      console.error(e);
      return;
    }
  }
  showPreviewModalWithFile(f);
}

function showPreviewModalWithFile(f) {
  activePreviewFile = f;
  const modal = document.getElementById('previewModal');
  const body = document.getElementById('modalPreviewBody');
  const icon = document.getElementById('modalFileIcon');
  const name = document.getElementById('modalFileName');
  const size = document.getElementById('modalFileSize');
  const downloadBtn = document.getElementById('modalDownloadBtn');

  icon.textContent = getFileIcon(f.name, f.mime);
  name.textContent = f.name;
  size.textContent = formatBytes(f.size);
  downloadBtn.href = '/api/download/' + f.id;
  downloadBtn.setAttribute('download', f.name);
  downloadBtn.onclick = (e) => {
    e.preventDefault();
    startChunkDownload(f);
  };

  body.innerHTML = '';
  const viewUrl = '/api/view/' + f.id;
  const fullStreamUrl = window.location.origin + viewUrl;

  if (isImage(f.name, f.mime)) {
    const img = document.createElement('img');
    img.className = 'preview-img';
    img.src = viewUrl;
    img.alt = f.name;
    body.appendChild(img);
  } else if (isVideo(f.name, f.mime)) {
    const container = document.createElement('div');
    container.className = 'preview-video-container';
    container.innerHTML = `
      <video id="activeVideoPlayer" class="preview-video" controls autoplay playsinline preload="metadata">
        <source src="${viewUrl}" type="video/webm">
        <source src="${viewUrl}" type="video/mp4">
        <source src="${viewUrl}" type="video/x-matroska">
        Your browser does not support HTML5 video playback.
      </video>
      <div id="videoFallbackCard" class="video-fallback-card hidden">
        <div class="video-fallback-title">⚠️ Browser Codec Notice</div>
        <p class="video-fallback-desc">Your browser cannot decode this stream (e.g. MKV with AC3/DTS/HEVC). Stream it directly in VLC — no downloading needed:</p>
        <div class="video-stream-url-box">
          <input type="text" readonly id="videoStreamUrlInput" placeholder="Generating stream link…" class="stream-url-input" />
          <button id="btnCopyStreamUrl" class="btn-copy-url">Copy</button>
        </div>
        <div class="video-fallback-btns">
          <button id="btnOpenVLC" class="btn-video-action">📺 Open in VLC…</button>
          <a id="btnDirectStreamLink" href="${viewUrl}" target="_blank" class="btn-video-action secondary">↗ Stream Tab</a>
          <button class="btn-video-action secondary btn-chunk-dl-trigger">⬇ Download</button>
        </div>
      </div>
      <div class="preview-bottom-actions">
        <button id="btnToggleExternalPlayer" class="btn-preview-action">📺 Open in VLC / External Player</button>
        <a href="${viewUrl}" target="_blank" rel="noopener noreferrer" class="btn-preview-action">↗ Direct Stream</a>
        <button class="btn-preview-action btn-chunk-dl-trigger">⬇ Resumable Download</button>
      </div>
    `;

    const video = container.querySelector('#activeVideoPlayer');
    const fallbackCard = container.querySelector('#videoFallbackCard');
    const copyBtn = container.querySelector('#btnCopyStreamUrl');
    const copyInput = container.querySelector('#videoStreamUrlInput');
    const toggleBtn = container.querySelector('#btnToggleExternalPlayer');
    const vlcBtn = container.querySelector('#btnOpenVLC');
    const directLink = container.querySelector('#btnDirectStreamLink');

    let tokenUrl = null;
    let tokenFetching = false;

    async function fetchStreamToken() {
      if (tokenUrl) return tokenUrl;
      if (tokenFetching) {
        // wait until done
        await new Promise(resolve => {
          const check = setInterval(() => { if (tokenUrl) { clearInterval(check); resolve(); } }, 100);
        });
        return tokenUrl;
      }
      tokenFetching = true;
      try {
        const res = await fetch('/api/stream-token/' + f.id, { method: 'POST' });
        if (!res.ok) throw new Error('Token request failed: ' + res.status);
        const data = await res.json();
        tokenUrl = window.location.origin + data.url;
      } catch (err) {
        console.warn('Stream token failed, token-less URL used — VLC may fail if auth required', err);
        tokenUrl = null; // mark failure
      }
      tokenFetching = false;
      return tokenUrl;
    }

    function updateFallbackUI(url) {
      if (!url) {
        copyInput.value = 'Failed to generate stream token. Restart preview and try again.';
        vlcBtn.textContent = '❌ Token error — retry';
        return;
      }
      copyInput.value = url;
      directLink.href = url;
      vlcBtn.textContent = '📺 Open in VLC';
    }

    // Intercept VLC button — fetch token FIRST, then launch
    vlcBtn.addEventListener('click', async () => {
      vlcBtn.textContent = '⏳ Generating link…';
      vlcBtn.disabled = true;
      const url = await fetchStreamToken();
      vlcBtn.disabled = false;
      if (!url) {
        updateFallbackUI(null);
        return;
      }
      updateFallbackUI(url);
      // Launch VLC via protocol handler
      window.location.href = 'vlc://' + url;
    });

    video.onerror = async () => {
      fallbackCard.classList.remove('hidden');
      // Pre-fetch token in background so it's ready when user clicks VLC
      fetchStreamToken().then(url => { if (url) updateFallbackUI(url); });
    };

    toggleBtn?.addEventListener('click', () => {
      fallbackCard.classList.toggle('hidden');
      // Pre-fetch token in background when panel opens
      if (!fallbackCard.classList.contains('hidden') && !tokenUrl) {
        fetchStreamToken().then(url => { if (url) updateFallbackUI(url); });
      }
    });

    copyBtn?.addEventListener('click', async () => {
      const url = await fetchStreamToken();
      if (!url) { copyInput.value = 'Token error — close and reopen preview.'; return; }
      updateFallbackUI(url);
      try {
        await navigator.clipboard.writeText(url);
        copyBtn.textContent = '✓ Copied!';
        setTimeout(() => { copyBtn.textContent = 'Copy'; }, 2000);
      } catch (_) {
        copyInput.select();
        document.execCommand('copy');
      }
    });

    container.querySelectorAll('.btn-chunk-dl-trigger').forEach(btn => {
      btn.onclick = () => startChunkDownload(f);
    });

    body.appendChild(container);


  } else if (isAudio(f.name, f.mime)) {
    const wrap = document.createElement('div');
    wrap.className = 'preview-audio-wrap';
    wrap.innerHTML = `
      <div class="preview-audio-icon">🎵</div>
      <h3>${escapeHtml(f.name)}</h3>
      <audio controls autoplay src="${viewUrl}"></audio>
      <div class="preview-bottom-actions" style="margin-top:16px;">
        <button class="btn-preview-action btn-chunk-dl-trigger" style="color:var(--drive-text); background:#f0f4f9;">
          ⬇ Resumable Download
        </button>
      </div>
    `;
    wrap.querySelector('.btn-chunk-dl-trigger').onclick = () => startChunkDownload(f);
    body.appendChild(wrap);
  } else if (isPdf(f.name, f.mime)) {
    const container = document.createElement('div');
    container.className = 'pdf-preview-container';
    container.innerHTML = `
      <iframe src="${viewUrl}#view=FitH" class="preview-pdf-frame" title="PDF Preview"></iframe>
      <div class="preview-bottom-actions">
        <a href="${viewUrl}" target="_blank" rel="noopener noreferrer" class="btn-preview-action">
          ↗ Open in New Window
        </a>
        <button class="btn-preview-action btn-chunk-dl-trigger">
          ⬇ Resumable Download
        </button>
      </div>
    `;
    container.querySelector('.btn-chunk-dl-trigger').onclick = () => startChunkDownload(f);
    body.appendChild(container);
  } else if (isText(f.name, f.mime)) {
    const pre = document.createElement('pre');
    pre.className = 'preview-text-box';
    pre.textContent = 'Loading text preview...';
    body.appendChild(pre);
    fetch(viewUrl)
      .then(r => r.text())
      .then(t => { pre.textContent = t; })
      .catch(() => { pre.textContent = 'Failed to load text preview.'; });
  } else {
    const box = document.createElement('div');
    box.className = 'preview-generic-box';
    box.innerHTML = `
      <div class="generic-icon">${getFileIcon(f.name, f.mime)}</div>
      <div class="generic-title">${escapeHtml(f.name)}</div>
      <div class="generic-desc">${formatBytes(f.size)} • No preview available</div>
      <button class="modal-btn-download btn-chunk-dl-trigger" style="margin-top:12px; border:none; cursor:pointer;">Resumable Download</button>
    `;
    box.querySelector('.btn-chunk-dl-trigger').onclick = () => startChunkDownload(f);
    body.appendChild(box);
  }

  modal.classList.remove('hidden');
}

function closePreviewModal(updateHash = true) {
  const modal = document.getElementById('previewModal');
  const body = document.getElementById('modalPreviewBody');
  const media = body.querySelectorAll('video, audio');
  media.forEach(m => m.pause());

  modal.classList.add('hidden');
  body.innerHTML = '';
  activePreviewFile = null;

  if (updateHash) {
    window.location.hash = '#/folders/' + (currentFolderId || 'root');
  }
}

// Resumable Chunk Download Manager
const CHUNK_SIZE = 4 * 1024 * 1024; // 4 MB chunks

let activeDownload = {
  fileId: null,
  fileName: '',
  fileSize: 0,
  mime: '',
  downloadedBytes: 0,
  chunks: [],
  state: 'IDLE', // 'IDLE' | 'DOWNLOADING' | 'PAUSED' | 'STOPPED' | 'COMPLETED'
  abortController: null,
  isLoopRunning: false,
  lastTime: 0,
  lastBytes: 0,
  speed: 0
};

function startChunkDownload(f) {
  if (!f || !f.id) return;

  if (activeDownload.state === 'DOWNLOADING' && activeDownload.fileId === f.id) {
    showDownloadDrawer(true);
    return;
  }

  if (activeDownload.state === 'DOWNLOADING') {
    stopChunkDownload();
  }

  activeDownload.fileId = f.id;
  activeDownload.fileName = f.name || 'download';
  activeDownload.fileSize = f.size || 0;
  activeDownload.mime = f.mime || 'application/octet-stream';
  activeDownload.downloadedBytes = 0;
  activeDownload.chunks = [];
  activeDownload.state = 'DOWNLOADING';
  activeDownload.lastTime = performance.now();
  activeDownload.lastBytes = 0;
  activeDownload.speed = 0;

  showDownloadDrawer(true);
  updateDrawerUI();

  if (activeDownload.fileSize <= 0) {
    const a = document.createElement('a');
    a.href = '/api/download/' + f.id;
    a.download = f.name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    activeDownload.state = 'COMPLETED';
    updateDrawerUI();
    return;
  }

  runDownloadLoop();
}

async function runDownloadLoop() {
  if (activeDownload.isLoopRunning) return;
  activeDownload.isLoopRunning = true;

  try {
    while (activeDownload.state === 'DOWNLOADING' && activeDownload.downloadedBytes < activeDownload.fileSize) {
      const start = activeDownload.downloadedBytes;
      const end = Math.min(start + CHUNK_SIZE - 1, activeDownload.fileSize - 1);

      activeDownload.abortController = new AbortController();

      let res;
      try {
        res = await fetch('/api/download/' + activeDownload.fileId, {
          headers: {
            'Range': `bytes=${start}-${end}`
          },
          signal: activeDownload.abortController.signal
        });
      } catch (err) {
        if (activeDownload.state === 'PAUSED' || activeDownload.state === 'STOPPED') {
          break;
        }
        console.error('Fetch chunk error:', err);
        await new Promise(r => setTimeout(r, 1000));
        continue;
      }

      if (activeDownload.state !== 'DOWNLOADING') break;

      if (!res.ok && res.status !== 206) {
        console.error('Chunk response not ok:', res.status);
        break;
      }

      const chunkBlob = await res.blob();
      if (activeDownload.state !== 'DOWNLOADING') break;

      activeDownload.chunks.push(chunkBlob);
      activeDownload.downloadedBytes += chunkBlob.size;

      const now = performance.now();
      const elapsed = (now - activeDownload.lastTime) / 1000;
      if (elapsed >= 0.5) {
        const bytesDiff = activeDownload.downloadedBytes - activeDownload.lastBytes;
        activeDownload.speed = bytesDiff / elapsed;
        activeDownload.lastTime = now;
        activeDownload.lastBytes = activeDownload.downloadedBytes;
      }

      updateDrawerUI();
    }

    if (activeDownload.state === 'DOWNLOADING' && activeDownload.downloadedBytes >= activeDownload.fileSize) {
      completeChunkDownload();
    }
  } catch (err) {
    console.error('Download loop error:', err);
  } finally {
    activeDownload.isLoopRunning = false;
  }
}

function completeChunkDownload() {
  activeDownload.state = 'COMPLETED';
  activeDownload.speed = 0;
  updateDrawerUI();

  try {
    const finalBlob = new Blob(activeDownload.chunks, { type: activeDownload.mime || 'application/octet-stream' });
    const blobUrl = URL.createObjectURL(finalBlob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = activeDownload.fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
  } catch (err) {
    console.error('Blob assemble error:', err);
    window.location.href = '/api/download/' + activeDownload.fileId;
  }
}

function pauseChunkDownload() {
  if (activeDownload.state !== 'DOWNLOADING') return;
  activeDownload.state = 'PAUSED';
  if (activeDownload.abortController) {
    activeDownload.abortController.abort();
  }
  activeDownload.speed = 0;
  updateDrawerUI();
}

function resumeChunkDownload() {
  if (activeDownload.state !== 'PAUSED') return;
  activeDownload.state = 'DOWNLOADING';
  activeDownload.lastTime = performance.now();
  activeDownload.lastBytes = activeDownload.downloadedBytes;
  updateDrawerUI();
  runDownloadLoop();
}

function restartChunkDownload() {
  if (activeDownload.abortController) {
    activeDownload.abortController.abort();
  }
  activeDownload.downloadedBytes = 0;
  activeDownload.chunks = [];
  activeDownload.state = 'DOWNLOADING';
  activeDownload.lastTime = performance.now();
  activeDownload.lastBytes = 0;
  activeDownload.speed = 0;
  updateDrawerUI();
  runDownloadLoop();
}

function stopChunkDownload() {
  activeDownload.state = 'STOPPED';
  if (activeDownload.abortController) {
    activeDownload.abortController.abort();
  }
  activeDownload.downloadedBytes = 0;
  activeDownload.chunks = [];
  activeDownload.speed = 0;
  updateDrawerUI();
}

function showDownloadDrawer(show) {
  const drawer = document.getElementById('downloadDrawer');
  if (drawer) {
    drawer.classList.toggle('hidden', !show);
  }
}

function updateDrawerUI() {
  const nameEl = document.getElementById('drawerFileName');
  const barEl = document.getElementById('downloadProgressBar');
  const statsEl = document.getElementById('downloadStatsText');
  const speedEl = document.getElementById('downloadSpeedText');
  const pauseBtn = document.getElementById('btnPauseResumeDownload');
  const pauseIcon = document.getElementById('pauseResumeIcon');
  const pauseLabel = document.getElementById('pauseResumeLabel');

  if (nameEl) nameEl.textContent = activeDownload.fileName || 'Download';

  const total = activeDownload.fileSize || 1;
  const current = activeDownload.downloadedBytes;
  const pct = Math.min(100, Math.max(0, (current / total) * 100));

  if (barEl) barEl.style.width = pct.toFixed(1) + '%';

  if (activeDownload.state === 'COMPLETED') {
    if (statsEl) statsEl.textContent = `Completed (${formatBytes(activeDownload.fileSize)})`;
    if (speedEl) speedEl.textContent = '100%';
    if (pauseIcon) pauseIcon.textContent = '✓';
    if (pauseLabel) pauseLabel.textContent = 'Done';
    if (pauseBtn) pauseBtn.disabled = true;
  } else if (activeDownload.state === 'PAUSED') {
    if (statsEl) statsEl.textContent = `${formatBytes(current)} / ${formatBytes(total)} (${pct.toFixed(0)}%) - Paused`;
    if (speedEl) speedEl.textContent = 'Paused';
    if (pauseIcon) pauseIcon.textContent = '▶';
    if (pauseLabel) pauseLabel.textContent = 'Resume';
    if (pauseBtn) pauseBtn.disabled = false;
  } else if (activeDownload.state === 'STOPPED') {
    if (statsEl) statsEl.textContent = 'Download stopped';
    if (speedEl) speedEl.textContent = '0 B/s';
    if (pauseIcon) pauseIcon.textContent = '▶';
    if (pauseLabel) pauseLabel.textContent = 'Resume';
    if (pauseBtn) pauseBtn.disabled = true;
  } else if (activeDownload.state === 'DOWNLOADING') {
    if (statsEl) statsEl.textContent = `${formatBytes(current)} / ${formatBytes(total)} (${pct.toFixed(1)}%)`;
    if (speedEl) speedEl.textContent = formatBytes(activeDownload.speed) + '/s';
    if (pauseIcon) pauseIcon.textContent = '⏸';
    if (pauseLabel) pauseLabel.textContent = 'Pause';
    if (pauseBtn) pauseBtn.disabled = false;
  }
}

// Search filtering
function handleSearch(term) {
  term = (term || '').trim().toLowerCase();
  const clearBtn = document.getElementById('btnClearSearch');

  if (!term) {
    clearBtn.classList.add('hidden');
    currentFolders = [...allFolders];
    currentFiles = [...allFiles];
  } else {
    clearBtn.classList.remove('hidden');
    currentFolders = allFolders.filter(f => f.name.toLowerCase().includes(term));
    currentFiles = allFiles.filter(f => f.name.toLowerCase().includes(term));
  }
  renderContent();
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Event Listeners
window.addEventListener('hashchange', parseRoute);

document.getElementById('btnCloseModal')?.addEventListener('click', () => closePreviewModal(true));
window.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closePreviewModal(true);
  }
});

document.getElementById('btnPauseResumeDownload')?.addEventListener('click', () => {
  if (activeDownload.state === 'DOWNLOADING') {
    pauseChunkDownload();
  } else if (activeDownload.state === 'PAUSED') {
    resumeChunkDownload();
  }
});

document.getElementById('btnRestartDownload')?.addEventListener('click', () => {
  restartChunkDownload();
});

document.getElementById('btnCancelDownload')?.addEventListener('click', () => {
  stopChunkDownload();
});

document.getElementById('btnCloseDrawer')?.addEventListener('click', () => {
  showDownloadDrawer(false);
});

// View Toggle
document.getElementById('btnViewToggle')?.addEventListener('click', () => {
  isListView = !isListView;
  document.getElementById('iconGridView').classList.toggle('hidden', isListView);
  document.getElementById('iconListView').classList.toggle('hidden', !isListView);
  renderContent();
});

// Refresh button
document.getElementById('btnRefresh')?.addEventListener('click', () => {
  loadFolder(currentFolderId || 'root');
});

// Search input
const searchInput = document.getElementById('searchInput');
searchInput?.addEventListener('input', (e) => {
  handleSearch(e.target.value);
});
document.getElementById('btnClearSearch')?.addEventListener('click', () => {
  searchInput.value = '';
  handleSearch('');
  searchInput.focus();
});

// Home Logo button
document.getElementById('btnLogoHome')?.addEventListener('click', () => {
  window.location.hash = '#/folders/root';
});

// Logout
document.getElementById('btnLogout')?.addEventListener('click', async () => {
  try { await fetch('/api/logout', { method: 'POST' }); } catch (ignore) {}
  window.location.href = '/login';
});

// Mobile Sidebar Toggle and Backdrop Handlers
function setSidebarOpen(open) {
  const sidebar = document.getElementById('driveSidebar');
  const backdrop = document.getElementById('sidebarBackdrop');
  if (sidebar) sidebar.classList.toggle('open', open);
  if (backdrop) backdrop.classList.toggle('active', open);
}

document.getElementById('btnToggleSidebar')?.addEventListener('click', () => {
  const sidebar = document.getElementById('driveSidebar');
  const isOpen = sidebar && sidebar.classList.contains('open');
  setSidebarOpen(!isOpen);
});

document.getElementById('btnCloseSidebar')?.addEventListener('click', () => {
  setSidebarOpen(false);
});

document.getElementById('sidebarBackdrop')?.addEventListener('click', () => {
  setSidebarOpen(false);
});

document.getElementById('navMyDrive')?.addEventListener('click', () => setSidebarOpen(false));
document.getElementById('navShared')?.addEventListener('click', () => setSidebarOpen(false));

// Load user profile and start
(async () => {
  try {
    const res = await api('/api/me');
    const u = await res.json();
    const uname = u.username || u.userId || 'User';
    document.getElementById('userName').textContent = uname;
    document.getElementById('avatarInitial').textContent = uname.charAt(0).toUpperCase();
  } catch (ignore) {}

  parseRoute();
})();
