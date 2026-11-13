package com.localshare;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.localshare.domain.model.AccessRequest;
import com.localshare.domain.model.SharedResource;
import com.localshare.ftp.FtpServerManager;
import com.localshare.notification.AccessRequestApprovalHelper;
import com.localshare.notification.AccessRequestNotifier;
import com.localshare.persistence.AccessRequestDao;
import com.localshare.persistence.DbHelper;
import com.localshare.persistence.SharedResourceDao;
import com.localshare.server.ServerRuntime;
import com.localshare.service.FileShareService;
import com.localshare.storage.SafStorageGateway;
import com.localshare.ui.AccessRequestAdapter;
import com.localshare.ui.DailyDownloadsChartView;
import com.localshare.ui.LogAdapter;
import com.localshare.ui.SharedResourceAdapter;
import com.localshare.util.AppPreferences;
import com.localshare.util.NetworkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class MainActivity extends AppCompatActivity implements AccessRequestNotifier.OnAccessRequestsChangeListener {

    // Top Status Header
    private View pillServerStatus, dotStatus;
    private TextView tvTopStatusBadge;

    // Tab Containers
    private View tabHome, tabShared, tabLogs, tabSettings;
    private BottomNavigationView bottomNav;

    // Home Tab Controls
    private TextView tvStatus, tvUrl;
    private ImageView ivQr, btnCopyUrl;
    private MaterialButton btnStart, btnStop;
    private TextView tvFtpStatus, tvFtpUrl;
    private ImageView btnCopyFtpUrl;
    private MaterialButton btnStartFtp, btnStopFtp;
    private TextView tvStatDevices, tvStatLoad, tvStatRate, tvStatRequests;
    private DailyDownloadsChartView chartWeeklyDownloads;
    private TextView tvWeeklyTotalBadge;
    private TextView tvNoRequests;
    private RecyclerView rvRequests;
    private AccessRequestAdapter requestAdapter;

    // Shared Tab Controls
    private ImageView btnBackShared;
    private TextView tvSharedTitle, tvSharedPath, tvSharedCountBadge;
    private MaterialButton btnSharedAddFolder, btnSharedAddFile;
    private View layoutEmptyShared;
    private RecyclerView rvSharedTab;
    private SharedResourceAdapter sharedAdapter;
    private final Stack<FolderNavEntry> navStack = new Stack<>();

    // Logs Tab Controls
    private MaterialButton btnClearLogs;
    private TextView tvNoLogs;
    private RecyclerView rvLogsTab;
    private LogAdapter logAdapter;

    // Settings Tab Controls
    private TextInputEditText etSettingsPort;
    private MaterialButton btnSavePort, btnManageUsers, btnClearAllLogsSettings, btnResetDatabase;
    private TextInputEditText etSettingsFtpPort, etSettingsFtpUser, etSettingsFtpPass;
    private SwitchMaterial switchFtpSecure, switchFtpAnonymous;
    private MaterialButton btnSaveFtpSettings;

    // Persistence & Utilities
    private DbHelper dbHelper;
    private SharedResourceDao sharedResourceDao;
    private boolean isServerRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    private ActivityResultLauncher<Intent> openDocTreeLauncher;
    private ActivityResultLauncher<Intent> openDocLauncher;

    private static class FolderNavEntry {
        final String opaqueId;
        final String displayName;
        FolderNavEntry(String id, String name) {
            this.opaqueId = id;
            this.displayName = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AccessRequestNotifier.createNotificationChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        dbHelper = new DbHelper(this);
        sharedResourceDao = new SharedResourceDao(dbHelper);

        initViews();
        setupBottomNavigation();
        setupHomeTab();
        setupSharedTab();
        setupLogsTab();
        setupSettingsTab();
        setupFilePickers();

        // Broadcast listener for server lifecycle
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileShareService.BROADCAST_STATUS);
        filter.addAction(FileShareService.BROADCAST_REQUESTS_UPDATED);
        filter.addAction(FtpServerManager.BROADCAST_FTP_STATUS);
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        updateNetworkInfo();
        updateServerUi(false);
        updateFtpUi();
        loadPendingRequests();

        // Background metrics polling loop
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                loadPendingRequests();
                updateLiveMetrics();
                handler.postDelayed(this, 1500L);
            }
        };

        // Listen for new logs
        ServerRuntime.getInstance().addLogListener(new ServerRuntime.LogListener() {
            @Override
            public void onNewLog(ServerRuntime.LogEntry entry) {
                if (logAdapter != null) {
                    logAdapter.addEntry(entry);
                    if (tvNoLogs != null) {
                        tvNoLogs.setVisibility(logAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    }
                }
            }

            @Override
            public void onLogsCleared() {
                if (logAdapter != null) {
                    logAdapter.clear();
                    if (tvNoLogs != null) {
                        tvNoLogs.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void initViews() {
        pillServerStatus = findViewById(R.id.pillServerStatus);
        dotStatus = findViewById(R.id.dotStatus);
        tvTopStatusBadge = findViewById(R.id.tvTopStatusBadge);

        tabHome = findViewById(R.id.tabHome);
        tabShared = findViewById(R.id.tabShared);
        tabLogs = findViewById(R.id.tabLogs);
        tabSettings = findViewById(R.id.tabSettings);
        bottomNav = findViewById(R.id.bottomNav);

        // Home
        tvStatus = findViewById(R.id.tvStatus);
        tvUrl = findViewById(R.id.tvUrl);
        ivQr = findViewById(R.id.ivQr);
        btnCopyUrl = findViewById(R.id.btnCopyUrl);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvFtpStatus = findViewById(R.id.tvFtpStatus);
        tvFtpUrl = findViewById(R.id.tvFtpUrl);
        btnCopyFtpUrl = findViewById(R.id.btnCopyFtpUrl);
        btnStartFtp = findViewById(R.id.btnStartFtp);
        btnStopFtp = findViewById(R.id.btnStopFtp);
        tvStatDevices = findViewById(R.id.tvStatDevices);
        tvStatLoad = findViewById(R.id.tvStatLoad);
        tvStatRate = findViewById(R.id.tvStatRate);
        tvStatRequests = findViewById(R.id.tvStatRequests);
        chartWeeklyDownloads = findViewById(R.id.chartWeeklyDownloads);
        tvWeeklyTotalBadge = findViewById(R.id.tvWeeklyTotalBadge);
        tvNoRequests = findViewById(R.id.tvNoRequests);
        rvRequests = findViewById(R.id.rvRequests);

        // Shared
        btnBackShared = findViewById(R.id.btnBackShared);
        tvSharedTitle = findViewById(R.id.tvSharedTitle);
        tvSharedPath = findViewById(R.id.tvSharedPath);
        tvSharedCountBadge = findViewById(R.id.tvSharedCountBadge);
        btnSharedAddFolder = findViewById(R.id.btnSharedAddFolder);
        btnSharedAddFile = findViewById(R.id.btnSharedAddFile);
        layoutEmptyShared = findViewById(R.id.layoutEmptyShared);
        rvSharedTab = findViewById(R.id.rvSharedTab);

        // Logs
        btnClearLogs = findViewById(R.id.btnClearLogs);
        tvNoLogs = findViewById(R.id.tvNoLogs);
        rvLogsTab = findViewById(R.id.rvLogsTab);

        // Settings
        etSettingsPort = findViewById(R.id.etSettingsPort);
        btnSavePort = findViewById(R.id.btnSavePort);
        btnManageUsers = findViewById(R.id.btnManageUsers);
        btnClearAllLogsSettings = findViewById(R.id.btnClearAllLogsSettings);
        btnResetDatabase = findViewById(R.id.btnResetDatabase);
        etSettingsFtpPort = findViewById(R.id.etSettingsFtpPort);
        switchFtpSecure = findViewById(R.id.switchFtpSecure);
        switchFtpAnonymous = findViewById(R.id.switchFtpAnonymous);
        etSettingsFtpUser = findViewById(R.id.etSettingsFtpUser);
        etSettingsFtpPass = findViewById(R.id.etSettingsFtpPass);
        btnSaveFtpSettings = findViewById(R.id.btnSaveFtpSettings);
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showTab(0);
                return true;
            } else if (id == R.id.nav_shared) {
                showTab(1);
                refreshSharedTab();
                return true;
            } else if (id == R.id.nav_logs) {
                showTab(2);
                refreshLogsTab();
                return true;
            } else if (id == R.id.nav_settings) {
                showTab(3);
                refreshSettingsTab();
                return true;
            }
            return false;
        });
    }

    private void showTab(int index) {
        tabHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabShared.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabLogs.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    }

    private void setupHomeTab() {
        btnStart.setOnClickListener(v -> {
            updateServerUi(true);
            Intent i = new Intent(this, FileShareService.class);
            i.setAction(FileShareService.ACTION_START);
            startForegroundService(i);
        });

        btnStop.setOnClickListener(v -> {
            updateServerUi(false);
            Intent i = new Intent(this, FileShareService.class);
            i.setAction(FileShareService.ACTION_STOP);
            startService(i);
        });

        btnCopyUrl.setOnClickListener(v -> {
            String url = tvUrl.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("WiShare URL", url);
            cm.setPrimaryClip(cd);
            Toast.makeText(this, "Server URL copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        btnStartFtp.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    FtpServerManager.getInstance().start(MainActivity.this);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "FTP Server started", Toast.LENGTH_SHORT).show();
                        updateFtpUi();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to start FTP: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        btnStopFtp.setOnClickListener(v -> {
            new Thread(() -> {
                FtpServerManager.getInstance().stop(MainActivity.this);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "FTP Server stopped", Toast.LENGTH_SHORT).show();
                    updateFtpUi();
                });
            }).start();
        });

        btnCopyFtpUrl.setOnClickListener(v -> {
            String url = tvFtpUrl.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("FTP URL", url);
            cm.setPrimaryClip(cd);
            Toast.makeText(this, "FTP URL copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        ivQr.setOnClickListener(v -> {
            Drawable drawable = ivQr.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
                ImageView largeIv = new ImageView(this);
                largeIv.setImageBitmap(bmp);
                largeIv.setPadding(32, 32, 32, 32);
                new AlertDialog.Builder(this)
                        .setTitle("Server QR Code")
                        .setView(largeIv)
                        .setPositiveButton("Close", null)
                        .show();
            } else {
                Toast.makeText(this, "Start server to generate QR code", Toast.LENGTH_SHORT).show();
            }
        });

        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        rvRequests.setNestedScrollingEnabled(false);
        requestAdapter = new AccessRequestAdapter(new AccessRequestAdapter.Listener() {
            @Override
            public void onApprove(AccessRequest ar) {
                approveRequest(ar);
            }

            @Override
            public void onReject(AccessRequest ar) {
                rejectRequest(ar);
            }
        });
        rvRequests.setAdapter(requestAdapter);
    }

    private void setupSharedTab() {
        rvSharedTab.setLayoutManager(new LinearLayoutManager(this));
        sharedAdapter = new SharedResourceAdapter(new SharedResourceAdapter.Listener() {
            @Override
            public void onItemClick(SharedResource resource) {
                if (resource.isFolder) {
                    navStack.push(new FolderNavEntry(resource.opaqueId, resource.displayName));
                    refreshSharedTab();
                } else {
                    Toast.makeText(MainActivity.this, resource.displayName, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRemove(SharedResource resource) {
                boolean isRoot = resource.parentOpaqueId == null || resource.parentOpaqueId.isEmpty();
                String type = resource.isFolder ? "folder" : "file";
                String msg = isRoot
                        ? "Stop sharing \"" + resource.displayName + "\"?\nConnected users will no longer be able to access this " + type + "."
                        : "Remove \"" + resource.displayName + "\" from sharing?\nIt will no longer be visible or accessible to connected users.";

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Remove from Sharing?")
                        .setMessage(msg)
                        .setPositiveButton("Remove", (d, w) -> {
                            if (isRoot) {
                                sharedResourceDao.delete(resource.opaqueId);
                            } else {
                                SafStorageGateway gw = new SafStorageGateway(MainActivity.this, dbHelper);
                                Uri uri = gw.resolveUri(resource.opaqueId);
                                String uriStr = uri != null ? uri.toString() : null;
                                sharedResourceDao.excludeResource(resource.opaqueId, uriStr);
                            }
                            Toast.makeText(MainActivity.this, "Removed \"" + resource.displayName + "\"", Toast.LENGTH_SHORT).show();
                            refreshSharedTab();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        rvSharedTab.setAdapter(sharedAdapter);

        btnBackShared.setOnClickListener(v -> {
            if (!navStack.isEmpty()) {
                navStack.pop();
                refreshSharedTab();
            }
        });

        btnSharedAddFolder.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            openDocTreeLauncher.launch(i);
        });

        btnSharedAddFile.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            openDocLauncher.launch(i);
        });
    }

    private void refreshSharedTab() {
        if (navStack.isEmpty()) {
            btnBackShared.setVisibility(View.GONE);
            tvSharedTitle.setText("Shared Folders & Files");
            tvSharedPath.setText("Location: Root");

            List<SharedResource> roots = sharedResourceDao.listRoots();
            sharedAdapter.setItems(roots);
            tvSharedCountBadge.setText(roots.size() + " item" + (roots.size() == 1 ? "" : "s"));

            if (roots.isEmpty()) {
                layoutEmptyShared.setVisibility(View.VISIBLE);
                rvSharedTab.setVisibility(View.GONE);
            } else {
                layoutEmptyShared.setVisibility(View.GONE);
                rvSharedTab.setVisibility(View.VISIBLE);
            }
        } else {
            FolderNavEntry current = navStack.peek();
            btnBackShared.setVisibility(View.VISIBLE);
            tvSharedTitle.setText(current.displayName);

            StringBuilder sb = new StringBuilder("Location: Root");
            for (FolderNavEntry e : navStack) {
                sb.append(" > ").append(e.displayName);
            }
            tvSharedPath.setText(sb.toString());

            List<SharedResource> folderChildren = new ArrayList<>();
            try {
                SafStorageGateway gateway = new SafStorageGateway(this, dbHelper);
                List<SafStorageGateway.ChildItem> children = gateway.listChildren(current.opaqueId);
                for (SafStorageGateway.ChildItem child : children) {
                    SharedResource r = new SharedResource();
                    r.opaqueId = child.opaqueId;
                    r.displayName = child.displayName;
                    r.isFolder = child.isFolder;
                    r.size = child.size;
                    r.addedAt = child.lastModified;
                    r.parentOpaqueId = current.opaqueId;
                    folderChildren.add(r);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            sharedAdapter.setItems(folderChildren);
            tvSharedCountBadge.setText(folderChildren.size() + " item" + (folderChildren.size() == 1 ? "" : "s"));

            if (folderChildren.isEmpty()) {
                layoutEmptyShared.setVisibility(View.VISIBLE);
                rvSharedTab.setVisibility(View.GONE);
            } else {
                layoutEmptyShared.setVisibility(View.GONE);
                rvSharedTab.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupLogsTab() {
        rvLogsTab.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter();
        rvLogsTab.setAdapter(logAdapter);

        btnClearLogs.setOnClickListener(v -> {
            ServerRuntime.getInstance().clearLogs();
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
        });

        refreshLogsTab();
    }

    private void refreshLogsTab() {
        List<ServerRuntime.LogEntry> logs = ServerRuntime.getInstance().getLogs();
        logAdapter.setItems(logs);
        tvNoLogs.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupSettingsTab() {
        refreshSettingsTab();

        btnSavePort.setOnClickListener(v -> {
            String pStr = etSettingsPort.getText() != null ? etSettingsPort.getText().toString().trim() : "";
            try {
                int port = Integer.parseInt(pStr);
                if (port < AppPreferences.MIN_PORT || port > AppPreferences.MAX_PORT) {
                    Toast.makeText(this, "Port must be between " + AppPreferences.MIN_PORT + " and " + AppPreferences.MAX_PORT, Toast.LENGTH_SHORT).show();
                    return;
                }
                AppPreferences.setServerPort(this, port);
                Toast.makeText(this, "Port updated to " + port, Toast.LENGTH_SHORT).show();
                if (!isServerRunning) {
                    updateNetworkInfo();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveFtpSettings.setOnClickListener(v -> {
            String pStr = etSettingsFtpPort.getText() != null ? etSettingsFtpPort.getText().toString().trim() : "";
            try {
                int port = Integer.parseInt(pStr);
                if (port < AppPreferences.MIN_PORT || port > AppPreferences.MAX_PORT) {
                    Toast.makeText(this, "Port must be between " + AppPreferences.MIN_PORT + " and " + AppPreferences.MAX_PORT, Toast.LENGTH_SHORT).show();
                    return;
                }
                AppPreferences.setFtpPort(this, port);
                AppPreferences.setFtpSecure(this, switchFtpSecure.isChecked());
                AppPreferences.setFtpAnonymousAllowed(this, switchFtpAnonymous.isChecked());

                String user = etSettingsFtpUser.getText() != null ? etSettingsFtpUser.getText().toString().trim() : "admin";
                String pass = etSettingsFtpPass.getText() != null ? etSettingsFtpPass.getText().toString() : "";
                if (!user.isEmpty()) AppPreferences.setFtpUsername(this, user);
                if (!pass.isEmpty()) AppPreferences.setFtpPassword(this, pass);

                Toast.makeText(this, "FTP settings saved", Toast.LENGTH_SHORT).show();
                updateFtpUi();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid FTP port number", Toast.LENGTH_SHORT).show();
            }
        });

        btnManageUsers.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            startActivity(intent);
        });

        btnClearAllLogsSettings.setOnClickListener(v -> {
            ServerRuntime.getInstance().clearLogs();
            Toast.makeText(this, "Activity logs cleared", Toast.LENGTH_SHORT).show();
        });

        btnResetDatabase.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Shared Data & Stats?")
                    .setMessage("This will remove all currently shared items and clear download statistics. User accounts will not be affected.")
                    .setPositiveButton("Reset", (d, w) -> {
                        try {
                            dbHelper.getWritableDatabase().execSQL("DELETE FROM shared_resources");
                            dbHelper.getWritableDatabase().execSQL("DELETE FROM excluded_resources");
                            dbHelper.getWritableDatabase().execSQL("DELETE FROM download_stats");
                            ServerRuntime.getInstance().clearLogs();
                            refreshSharedTab();
                            updateLiveMetrics();
                            Toast.makeText(this, "Shared resources reset", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Error resetting: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void refreshSettingsTab() {
        int currentPort = AppPreferences.getServerPort(this);
        etSettingsPort.setText(String.valueOf(currentPort));

        etSettingsFtpPort.setText(String.valueOf(AppPreferences.getFtpPort(this)));
        switchFtpSecure.setChecked(AppPreferences.isFtpSecure(this));
        switchFtpAnonymous.setChecked(AppPreferences.isFtpAnonymousAllowed(this));
        etSettingsFtpUser.setText(AppPreferences.getFtpUsername(this));
        etSettingsFtpPass.setText(AppPreferences.getFtpPassword(this));
    }

    private void setupFilePickers() {
        openDocTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    saveSharedResource(uri, true);
                    Toast.makeText(this, "Folder added to share", Toast.LENGTH_SHORT).show();
                    refreshSharedTab();
                }
            }
        });

        openDocLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    saveSharedResource(uri, false);
                    Toast.makeText(this, "File added to share", Toast.LENGTH_SHORT).show();
                    refreshSharedTab();
                }
            }
        });
    }

    private void saveSharedResource(Uri uri, boolean isFolder) {
        String name = null;
        try {
            androidx.documentfile.provider.DocumentFile df = isFolder ?
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri) :
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri);
            if (df != null && df.getName() != null) name = df.getName();
        } catch (Exception ignore) {}

        if (name == null || name.isEmpty()) {
            name = SafStorageGateway.cleanDisplayName(uri.getLastPathSegment());
        } else {
            name = SafStorageGateway.cleanDisplayName(name);
        }

        String opaque = com.localshare.security.SecureTokenGenerator.generateOpaqueId();
        ContentValues cv = new ContentValues();
        cv.put("opaque_id", opaque);
        cv.put("display_name", name);
        cv.put("saf_uri", uri.toString());
        cv.put("is_folder", isFolder ? 1 : 0);
        cv.put("added_at", System.currentTimeMillis());
        dbHelper.getWritableDatabase().insert("shared_resources", null, cv);
    }

    private void updateNetworkInfo() {
        String ip = NetworkUtil.getLocalIpv4();
        if (ip == null) ip = "192.168.1.100";
        int port = AppPreferences.getServerPort(this);
        String url = "http://" + ip + ":" + port + "/";
        tvUrl.setText(url);
        generateQr(url);
    }

    private void updateLiveMetrics() {
        ServerRuntime runtime = ServerRuntime.getInstance();
        tvStatDevices.setText(String.valueOf(runtime.getActiveConnections()));
        tvStatLoad.setText(runtime.getServerLoadPercent() + "%");
        tvStatRate.setText(formatServingRate(runtime.getCurrentServingRate()));
        tvStatRequests.setText(String.valueOf(runtime.getTotalRequests()));

        // Chart & Weekly Stats
        List<ServerRuntime.DayStat> stats = runtime.getWeeklyStats(this);
        chartWeeklyDownloads.setStats(stats);

        int totalWeekly = 0;
        for (ServerRuntime.DayStat s : stats) {
            totalWeekly += s.downloadCount;
        }
        tvWeeklyTotalBadge.setText(totalWeekly + " dl this week");
    }

    private String formatServingRate(double bytesPerSec) {
        if (bytesPerSec <= 0) return "0 KB/s";
        if (bytesPerSec < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }

    private void updateServerUi(boolean running) {
        isServerRunning = running;
        if (running) {
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.35f);
            btnStop.setEnabled(true);
            btnStop.setAlpha(1.0f);

            tvStatus.setText("Server Running • Online");
            tvStatus.setTextColor(Color.parseColor("#10B981"));

            tvTopStatusBadge.setText("ONLINE");
            tvTopStatusBadge.setTextColor(Color.parseColor("#10B981"));
            pillServerStatus.setBackgroundResource(R.drawable.bg_pill_green);
            dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
        } else {
            btnStart.setEnabled(true);
            btnStart.setAlpha(1.0f);
            btnStop.setEnabled(false);
            btnStop.setAlpha(0.35f);

            tvStatus.setText("Server Stopped • Offline");
            tvStatus.setTextColor(Color.parseColor("#EF4444"));

            tvTopStatusBadge.setText("OFFLINE");
            tvTopStatusBadge.setTextColor(Color.parseColor("#EF4444"));
            pillServerStatus.setBackgroundResource(R.drawable.bg_pill_red);
            dotStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
        }
    }

    private void loadPendingRequests() {
        try {
            AccessRequestDao dao = new AccessRequestDao(dbHelper);
            List<AccessRequest> list = dao.listPending();
            requestAdapter.setItems(list);
            if (tvNoRequests != null) {
                tvNoRequests.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
        } catch (Exception ignore) {}
    }

    private void approveRequest(AccessRequest ar) {
        try {
            boolean ok = AccessRequestApprovalHelper.approve(this, ar.userId, ar.username);
            if (ok) {
                Toast.makeText(this, "Approved " + ar.username, Toast.LENGTH_SHORT).show();
                ServerRuntime.getInstance().log("APPROVED", "Approved access for " + ar.username, "127.0.0.1");
            } else {
                Toast.makeText(this, "Error approving " + ar.username, Toast.LENGTH_SHORT).show();
            }
            loadPendingRequests();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void rejectRequest(AccessRequest ar) {
        try {
            boolean ok = AccessRequestApprovalHelper.reject(this, ar.userId, ar.username);
            if (ok) {
                Toast.makeText(this, "Declined " + ar.username, Toast.LENGTH_SHORT).show();
                ServerRuntime.getInstance().log("DENIED", "Declined access for " + ar.username, "127.0.0.1");
            } else {
                Toast.makeText(this, "Error declining " + ar.username, Toast.LENGTH_SHORT).show();
            }
            loadPendingRequests();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFtpUi() {
        boolean running = FtpServerManager.getInstance().isRunning();
        int port = FtpServerManager.getInstance().getPort();
        boolean sec = FtpServerManager.getInstance().isSecure();
        String url = FtpServerManager.getInstance().getFtpUrl(this);
        tvFtpUrl.setText(url);

        if (running) {
            tvFtpStatus.setText("FTP Running • Port " + port + (sec ? " (FTPS TLS)" : ""));
            tvFtpStatus.setTextColor(getColor(R.color.status_online));
            btnStartFtp.setEnabled(false);
            btnStopFtp.setEnabled(true);
        } else {
            tvFtpStatus.setText("FTP Stopped • Offline");
            tvFtpStatus.setTextColor(getColor(R.color.status_offline));
            btnStartFtp.setEnabled(true);
            btnStopFtp.setEnabled(false);
        }
    }

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent == null) return;
            if (FtpServerManager.BROADCAST_FTP_STATUS.equals(intent.getAction())) {
                updateFtpUi();
                return;
            }
            if (FileShareService.BROADCAST_REQUESTS_UPDATED.equals(intent.getAction())) {
                loadPendingRequests();
                return;
            }
            boolean running = intent.getBooleanExtra("running", false);
            int port = intent.getIntExtra("port", 8080);
            String ip = intent.getStringExtra("ip");
            String error = intent.getStringExtra("error");
            if (error != null) {
                tvStatus.setText("Error: " + error);
                tvStatus.setTextColor(Color.parseColor("#EF4444"));
                updateServerUi(false);
                return;
            }
            if (running) {
                String url = "http://" + ip + ":" + port + "/";
                tvUrl.setText(url);
                generateQr(url);
                updateServerUi(true);
            } else {
                updateNetworkInfo();
                updateServerUi(false);
            }
        }
    };

    private void generateQr(String url) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512);
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bmp = encoder.createBitmap(matrix);
            ivQr.setImageBitmap(bmp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccessRequestNotifier.addListener(this);
        loadPendingRequests();
        updateLiveMetrics();
        updateFtpUi();
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AccessRequestNotifier.removeListener(this);
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    public void onRequestsChanged() {
        runOnUiThread(this::loadPendingRequests);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AccessRequestNotifier.removeListener(this);
        handler.removeCallbacks(pollRunnable);
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignore) {}
    }
}
