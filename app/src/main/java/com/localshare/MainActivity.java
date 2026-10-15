package com.localshare;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.localshare.domain.model.AccessRequest;
import com.localshare.notification.AccessRequestApprovalHelper;
import com.localshare.notification.AccessRequestNotifier;
import com.localshare.persistence.AccessRequestDao;
import com.localshare.persistence.DbHelper;
import com.localshare.security.SecureTokenGenerator;
import com.localshare.server.ServerConfig;
import com.localshare.service.FileShareService;
import com.localshare.storage.SafStorageGateway;
import com.localshare.ui.AccessRequestAdapter;
import com.localshare.util.NetworkUtil;
import java.util.List;

/* JADX INFO: loaded from: classes11.dex */
public class MainActivity extends AppCompatActivity implements AccessRequestNotifier.OnAccessRequestsChangeListener {
    private MaterialButton btnPickFile;
    private MaterialButton btnPickFolder;
    private MaterialButton btnStart;
    private MaterialButton btnStop;
    private DbHelper dbHelper;
    private ImageView ivQr;
    private ActivityResultLauncher<Intent> openDocLauncher;
    private ActivityResultLauncher<Intent> openDocTreeLauncher;
    private Runnable pollRunnable;
    private AccessRequestAdapter requestAdapter;
    private RecyclerView rvRequests;
    private TextView tvNoRequests;
    private TextView tvStatus;
    private TextView tvUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() { // from class: com.localshare.MainActivity.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context c, Intent intent) {
            if (intent == null) {
                return;
            }
            if (FileShareService.BROADCAST_REQUESTS_UPDATED.equals(intent.getAction())) {
                MainActivity.this.loadPendingRequests();
                return;
            }
            boolean running = intent.getBooleanExtra("running", false);
            int port = intent.getIntExtra("port", ServerConfig.DEFAULT_PORT);
            String ip = intent.getStringExtra("ip");
            String error = intent.getStringExtra("error");
            if (error != null) {
                MainActivity.this.tvStatus.setText("Error: " + error);
                MainActivity.this.tvStatus.setTextColor(Color.parseColor("#DC2626"));
                MainActivity.this.updateServerUi(false);
            } else if (!running) {
                MainActivity.this.updateNetworkInfo();
                MainActivity.this.updateServerUi(false);
            } else {
                String url = "http://" + ip + ":" + port + "/";
                MainActivity.this.tvUrl.setText(url);
                MainActivity.this.generateQr(url);
                MainActivity.this.updateServerUi(true);
            }
        }
    };

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AccessRequestNotifier.createNotificationChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }
        this.dbHelper = new DbHelper(this);
        this.tvStatus = (TextView) findViewById(R.id.tvStatus);
        this.tvUrl = (TextView) findViewById(R.id.tvUrl);
        this.tvNoRequests = (TextView) findViewById(R.id.tvNoRequests);
        this.ivQr = (ImageView) findViewById(R.id.ivQr);
        this.btnStart = (MaterialButton) findViewById(R.id.btnStart);
        this.btnStop = (MaterialButton) findViewById(R.id.btnStop);
        this.btnPickFolder = (MaterialButton) findViewById(R.id.btnPickFolder);
        this.btnPickFile = (MaterialButton) findViewById(R.id.btnPickFile);
        this.rvRequests = (RecyclerView) findViewById(R.id.rvRequests);
        this.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        this.rvRequests.setNestedScrollingEnabled(false);
        this.requestAdapter = new AccessRequestAdapter(new AccessRequestAdapter.Listener() { // from class: com.localshare.MainActivity.1
            @Override // com.localshare.ui.AccessRequestAdapter.Listener
            public void onApprove(AccessRequest ar) {
                MainActivity.this.approveRequest(ar);
            }

            @Override // com.localshare.ui.AccessRequestAdapter.Listener
            public void onReject(AccessRequest ar) {
                MainActivity.this.rejectRequest(ar);
            }
        });
        this.rvRequests.setAdapter(this.requestAdapter);
        this.openDocTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda1
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                this.f$0.m154lambda$onCreate$0$comlocalshareMainActivity((ActivityResult) obj);
            }
        });
        this.openDocLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda2
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                this.f$0.m155lambda$onCreate$1$comlocalshareMainActivity((ActivityResult) obj);
            }
        });
        this.btnStart.setOnClickListener(new View.OnClickListener() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m156lambda$onCreate$2$comlocalshareMainActivity(view);
            }
        });
        this.btnStop.setOnClickListener(new View.OnClickListener() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m157lambda$onCreate$3$comlocalshareMainActivity(view);
            }
        });
        this.btnPickFolder.setOnClickListener(new View.OnClickListener() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m158lambda$onCreate$4$comlocalshareMainActivity(view);
            }
        });
        this.btnPickFile.setOnClickListener(new View.OnClickListener() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m159lambda$onCreate$5$comlocalshareMainActivity(view);
            }
        });
        this.ivQr.setOnClickListener(new View.OnClickListener() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m160lambda$onCreate$6$comlocalshareMainActivity(view);
            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileShareService.BROADCAST_STATUS);
        filter.addAction(FileShareService.BROADCAST_REQUESTS_UPDATED);
        registerReceiver(this.statusReceiver, filter, 4);
        updateNetworkInfo();
        updateServerUi(false);
        loadPendingRequests();
        this.pollRunnable = new Runnable() { // from class: com.localshare.MainActivity.2
            @Override // java.lang.Runnable
            public void run() {
                MainActivity.this.loadPendingRequests();
                MainActivity.this.handler.postDelayed(this, 1500L);
            }
        };
        sanitizeExistingResources();
    }

    /* JADX INFO: renamed from: lambda$onCreate$0$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m154lambda$onCreate$0$comlocalshareMainActivity(ActivityResult result) {
        Uri uri;
        if (result.getResultCode() == -1 && result.getData() != null && (uri = result.getData().getData()) != null) {
            getContentResolver().takePersistableUriPermission(uri, 3);
            Toast.makeText(this, "Folder added", 0).show();
            saveSharedResource(uri, true);
        }
    }

    /* JADX INFO: renamed from: lambda$onCreate$1$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m155lambda$onCreate$1$comlocalshareMainActivity(ActivityResult result) {
        Uri uri;
        if (result.getResultCode() == -1 && result.getData() != null && (uri = result.getData().getData()) != null) {
            getContentResolver().takePersistableUriPermission(uri, 1);
            saveSharedResource(uri, false);
            Toast.makeText(this, "File added", 0).show();
        }
    }

    /* JADX INFO: renamed from: lambda$onCreate$2$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m156lambda$onCreate$2$comlocalshareMainActivity(View v) {
        updateServerUi(true);
        Intent i = new Intent(this, (Class<?>) FileShareService.class);
        i.setAction(FileShareService.ACTION_START);
        startForegroundService(i);
    }

    /* JADX INFO: renamed from: lambda$onCreate$3$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m157lambda$onCreate$3$comlocalshareMainActivity(View v) {
        updateServerUi(false);
        Intent i = new Intent(this, (Class<?>) FileShareService.class);
        i.setAction(FileShareService.ACTION_STOP);
        startService(i);
    }

    /* JADX INFO: renamed from: lambda$onCreate$4$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m158lambda$onCreate$4$comlocalshareMainActivity(View v) {
        Intent i = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
        this.openDocTreeLauncher.launch(i);
    }

    /* JADX INFO: renamed from: lambda$onCreate$5$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m159lambda$onCreate$5$comlocalshareMainActivity(View v) {
        Intent i = new Intent("android.intent.action.OPEN_DOCUMENT");
        i.addCategory("android.intent.category.OPENABLE");
        i.setType("*/*");
        this.openDocLauncher.launch(i);
    }

    /* JADX INFO: renamed from: lambda$onCreate$6$com-localshare-MainActivity, reason: not valid java name */
    /* synthetic */ void m160lambda$onCreate$6$comlocalshareMainActivity(View v) {
        Drawable drawable = this.ivQr.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            ImageView largeIv = new ImageView(this);
            largeIv.setImageBitmap(bmp);
            largeIv.setPadding(32, 32, 32, 32);
            new AlertDialog.Builder(this).setTitle("Server QR Code").setView(largeIv).setPositiveButton("Close", (DialogInterface.OnClickListener) null).show();
            return;
        }
        Toast.makeText(this, "Start server to generate QR code", 0).show();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        AccessRequestNotifier.addListener(this);
        loadPendingRequests();
        this.handler.removeCallbacks(this.pollRunnable);
        this.handler.post(this.pollRunnable);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
        AccessRequestNotifier.removeListener(this);
        this.handler.removeCallbacks(this.pollRunnable);
    }

    @Override // com.localshare.notification.AccessRequestNotifier.OnAccessRequestsChangeListener
    public void onRequestsChanged() {
        runOnUiThread(new Runnable() { // from class: com.localshare.MainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.loadPendingRequests();
            }
        });
    }

    private void sanitizeExistingResources() {
        try {
            SQLiteDatabase db = this.dbHelper.getWritableDatabase();
            Cursor c = db.query("shared_resources", new String[]{"opaque_id", "display_name"}, null, null, null, null, null);
            while (c.moveToNext()) {
                try {
                    String oid = c.getString(0);
                    String curName = c.getString(1);
                    if (curName != null && (curName.contains(":") || curName.contains("/"))) {
                        String clean = SafStorageGateway.cleanDisplayName(curName);
                        ContentValues cv = new ContentValues();
                        cv.put("display_name", clean);
                        db.update("shared_resources", cv, "opaque_id=?", new String[]{oid});
                    }
                } catch (Throwable th) {
                    if (c != null) {
                        try {
                            c.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
        }
    }

    private void saveSharedResource(Uri uri, boolean z) {
        String strCleanDisplayName;
        DocumentFile documentFileFromSingleUri;
        String name = null;
        try {
            if (z) {
                documentFileFromSingleUri = DocumentFile.fromTreeUri(this, uri);
            } else {
                documentFileFromSingleUri = DocumentFile.fromSingleUri(this, uri);
            }
            if (documentFileFromSingleUri != null && documentFileFromSingleUri.getName() != null) {
                name = documentFileFromSingleUri.getName();
            }
        } catch (Exception e) {
        }
        if (name == null || name.isEmpty()) {
            strCleanDisplayName = SafStorageGateway.cleanDisplayName(uri.getLastPathSegment());
        } else {
            strCleanDisplayName = SafStorageGateway.cleanDisplayName(name);
        }
        String strGenerateOpaqueId = SecureTokenGenerator.generateOpaqueId();
        ContentValues contentValues = new ContentValues();
        contentValues.put("opaque_id", strGenerateOpaqueId);
        contentValues.put("display_name", strCleanDisplayName);
        contentValues.put("saf_uri", uri.toString());
        contentValues.put("is_folder", Integer.valueOf(z ? 1 : 0));
        contentValues.put("added_at", Long.valueOf(System.currentTimeMillis()));
        this.dbHelper.getWritableDatabase().insert("shared_resources", null, contentValues);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNetworkInfo() {
        String ip = NetworkUtil.getLocalIpv4();
        if (ip == null) {
            ip = "192.168.1.100";
        }
        this.tvUrl.setText("http://" + ip + ":8080/");
        generateQr("http://" + ip + ":8080/");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateServerUi(boolean running) {
        if (running) {
            this.btnStart.setEnabled(false);
            this.btnStart.setAlpha(0.35f);
            this.btnStart.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#94A3B8")));
            this.btnStart.setTextColor(Color.parseColor("#475569"));
            this.btnStart.setElevation(0.0f);
            this.btnStop.setEnabled(true);
            this.btnStop.setAlpha(1.0f);
            this.btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
            this.btnStop.setTextColor(-1);
            this.btnStop.setElevation(8.0f);
            this.tvStatus.setText("Server Running • Online");
            this.tvStatus.setTextColor(Color.parseColor("#16A34A"));
            return;
        }
        this.btnStart.setEnabled(true);
        this.btnStart.setAlpha(1.0f);
        this.btnStart.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#16A34A")));
        this.btnStart.setTextColor(-1);
        this.btnStart.setElevation(8.0f);
        this.btnStop.setEnabled(false);
        this.btnStop.setAlpha(0.35f);
        this.btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#94A3B8")));
        this.btnStop.setTextColor(Color.parseColor("#475569"));
        this.btnStop.setElevation(0.0f);
        this.tvStatus.setText("Server Stopped • Offline");
        this.tvStatus.setTextColor(Color.parseColor("#DC2626"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadPendingRequests() {
        try {
            AccessRequestDao dao = new AccessRequestDao(this.dbHelper);
            List<AccessRequest> list = dao.listPending();
            this.requestAdapter.setItems(list);
            if (this.tvNoRequests != null) {
                this.tvNoRequests.setVisibility(list.isEmpty() ? 0 : 8);
            }
        } catch (Exception e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void approveRequest(AccessRequest ar) {
        try {
            boolean ok = AccessRequestApprovalHelper.approve(this, ar.userId, ar.username);
            if (ok) {
                Toast.makeText(this, "Approved " + ar.username, 0).show();
            } else {
                Toast.makeText(this, "Error approving " + ar.username, 0).show();
            }
            loadPendingRequests();
        } catch (Exception e) {
            Toast.makeText(this, "Error approving: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void rejectRequest(AccessRequest ar) {
        try {
            boolean ok = AccessRequestApprovalHelper.reject(this, ar.userId, ar.username);
            if (ok) {
                Toast.makeText(this, "Declined " + ar.username, 0).show();
            } else {
                Toast.makeText(this, "Error declining " + ar.username, 0).show();
            }
            loadPendingRequests();
        } catch (Exception e) {
            Toast.makeText(this, "Error denying: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void generateQr(String url) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512);
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bmp = encoder.createBitmap(matrix);
            this.ivQr.setImageBitmap(bmp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        AccessRequestNotifier.removeListener(this);
        this.handler.removeCallbacks(this.pollRunnable);
        try {
            unregisterReceiver(this.statusReceiver);
        } catch (Exception e) {
        }
    }
}
