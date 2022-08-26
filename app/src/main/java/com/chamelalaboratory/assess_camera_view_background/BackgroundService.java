package com.chamelalaboratory.assess_camera_view_background;


import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class BackgroundService extends Service {

    public static final String BROADCAST_ACTION = "com.chamelalaboratory.assess_camera_view_background.BackgroundService";
    private final Handler handler = new Handler();
    private final IBinder binder = new MyBinder();
    Boolean flag;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private View camView;
    private CameraSurfaceView preview;
    private Camera myCamera;
    private int fullScreen = 0;
    private Button minimize;
    private Button start;
    private MediaRecorder mediaRecorder;
    private Boolean isRecording;
    private String videoFilename = "MyRec";

    private final Runnable sendUpdates = () -> {
        Log.e("BroadcastReceiver--->", "working");
        DisplayLoggingInfo();
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        final String filename = intent.getStringExtra("filename");
        if (filename != null) SetFilename(filename);

    }

    public void SetFilename(String filename) {
        videoFilename = filename;
    }


    @SuppressLint({"InflateParams", "SetTextI18n", "RtlHardcoded"})
    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        isRecording = false;
        camView = inflater.inflate(R.layout.service_layout, null);
        FrameLayout frameLayout = camView.findViewById(R.id.preview);
        minimize = camView.findViewById(R.id.minimize);
        minimize.setVisibility(ImageButton.GONE);
        start = camView.findViewById(R.id.record);
        start.setVisibility(Button.GONE);
        start.setOnClickListener(v -> {
            if (!isRecording) {
                if (prepareMediaRecorder()) {
                    try {
                        mediaRecorder.start();
                        isRecording = true;
                        start.setText("STOP");
                        handler.post(sendUpdates);
                    } catch (Exception e) {
                        Log.getStackTraceString(e);
                    }
                }
            } else {
                mediaRecorder.stop(); // stop the recording
                releaseMediaRecorder();
                isRecording = false;
                start.setText("START");
                handler.post(sendUpdates);
            }

        });
        minimize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                params.width = 500;
                params.height = 300;
                minimize.setVisibility(ImageButton.GONE);
                start.setVisibility(Button.GONE);
                windowManager.updateViewLayout(camView, params);
                fullScreen = 0;
            }
        });

        handleCamera(frameLayout);

    }

    private void DisplayLoggingInfo() {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra("status", isRecording.toString());
        intent.putExtra("filename", videoFilename);
        sendBroadcast(intent);
        Log.e("Receiver--->", "" + isRecording.toString());
    }

    @SuppressLint("SdCardPath")
    private boolean prepareMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        myCamera.unlock();
        mediaRecorder.setCamera(myCamera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        String fName = videoFilename + ".mp4";
        String filepath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath()) + "/" + fName;
        mediaRecorder.setOutputFile(filepath);
        mediaRecorder.setPreviewDisplay(preview.getHolder().getSurface());
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            Log.e("IllegalStateEx--->", Log.getStackTraceString(e));
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            Log.e("IOException--->", Log.getStackTraceString(e));
            return false;
        } catch (Exception e) {
            Log.e("Exception--->", Log.getStackTraceString(e));
        }
        return true;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            myCamera.lock(); // lock camera for later use
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (camView != null) windowManager.removeView(camView);
    }

    private void handleCamera(FrameLayout frameLayout) {
        try {
            myCamera = Camera.open();

            if (myCamera == null) {
                Toast.makeText(getApplicationContext(), "Failed to Open Camera", Toast.LENGTH_LONG).show();
            }
            myCamera.setDisplayOrientation(90);

            preview = new CameraSurfaceView(getApplicationContext(), myCamera);
            frameLayout.addView(preview);

            params = new WindowManager.LayoutParams(500, 300,
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ? TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 100;
            params.y = 150;


            try {
                windowManager.addView(camView, params);
            } catch (Exception e) {
                e.printStackTrace();
            }

            camView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            Log.e("w", "1");
                            flag = false;
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!flag && fullScreen == 0) {
                                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                                windowManager.updateViewLayout(camView, params);
                                minimize.setVisibility(ImageButton.VISIBLE);
                                start.setVisibility(Button.VISIBLE);
                                fullScreen = 1;
                            }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(camView, params);
                            Log.e("w", "3");
                            flag = true;
                            return true;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class MyBinder extends Binder {
        BackgroundService getService() {
            return BackgroundService.this;
        }
    }
}

