package com.chamelalaboratory.assess_camera_view_background;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    public final static int REQUEST_CODE = 0;
    Boolean recording;
    Button close;
    private BackgroundService serviceBinder;
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            serviceBinder = ((BackgroundService.MyBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            serviceBinder = null;
        }
    };
    private String filename;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDate(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        close = findViewById(R.id.close);
        close.setOnClickListener(v -> {
            if (filename.equalsIgnoreCase("null"))
                Toast.makeText(MainActivity.this, "Video is necessary", Toast.LENGTH_LONG).show();
            else {
                if (!recording) {
                    try {
                        stopService(new Intent(MainActivity.this, BackgroundService.class));
                        unbindService(mConnection);
                        finish();
                        System.exit(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else
                    Toast.makeText(MainActivity.this, "Stop the video to continue", Toast.LENGTH_LONG).show();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkDrawOverlayPermission();
            if (!hasCameraPermissionGranted()) {
                requestCameraPermission();

            } else {
                startBackgroundServices();
            }
        } else {
            startBackgroundServices();
        }
    }

    private void updateDate(Intent intent) {
        recording = Boolean.valueOf(intent.getStringExtra("status"));
        filename = intent.getStringExtra("filename");
        Log.e("Receiver--->", "" + recording.toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, new IntentFilter(
                BackgroundService.BROADCAST_ACTION));
    }

    public void checkDrawOverlayPermission() {
        //check if we already  have permission to draw over other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                //if not construct intent to request permission
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                //request permission via start activity for result
                startActivityForResult(intent, REQUEST_CODE);
            }
    }

    private boolean hasCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //check if received result code
        //is equal our requested code for draw permission
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this);// continue here - permission was granted
                startBackgroundServices();
            }
        }


    }

    private void startBackgroundServices() {


        recording = false;
        Intent bindIntent = new Intent(MainActivity.this, BackgroundService.class);
        Random r = new Random();
        int i1 = r.nextInt(80 - 65) + 65;
        filename = "video-" + i1;
        bindIntent.putExtra("Filename", filename);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
        startService(new Intent(MainActivity.this, BackgroundService.class));
    }
}