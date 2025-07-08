package com.example.webrtcdemo.messenger.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.webrtcdemo.binder.IWebRtcCallback;
import com.example.webrtcdemo.binder.IWebRtcService;
import com.example.webrtcdemo.messenger.utils.EglUtils;
import com.example.webrtcdemo.messenger.utils.WebRtcHolder;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;


public class CallerActivity extends AppCompatActivity {

    private static final String TAG = "CallerActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;

    private IWebRtcService webRtcService;
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    private final IWebRtcCallback callback = new IWebRtcCallback.Stub() {
        @Override
        public void onLocalVideoTrackCreated(String trackId) throws RemoteException {
            Log.d(TAG, "onLocalVideoTrack received: " + trackId);
            runOnUiThread(() -> showLocalTrack(trackId));
        }

        @Override
        public void onRemoteVideoTrackCreated(String trackId) throws RemoteException {
            Log.d(TAG, "onRemoteVideoTrackCreated: " + trackId);
            runOnUiThread(() -> showRemoteTrack(trackId));
        }

    };

    private void showLocalTrack(String trackId) {
        Log.d(TAG, "Trying to show local track: " + trackId);
        VideoTrack track = WebRtcHolder.getVideoTrack(trackId);
        if (track != null) {
            track.addSink(localRenderer);
            Log.d(TAG, "Local track added as sink");
        } else {
            Log.e(TAG, "VideoTrack not found in holder.");
        }
    }

    private void showRemoteTrack(String trackId) {
        VideoTrack track = WebRtcHolder.getVideoTrack(trackId);
        if (track != null) {
            track.addSink(remoteRenderer);
        } else {
            Log.e(TAG, "Remote VideoTrack not found in holder.");
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            webRtcService = IWebRtcService.Stub.asInterface(service);
            try {
                webRtcService.registerCallback(callback);
                webRtcService.startCall();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webRtcService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        localRenderer = new SurfaceViewRenderer(this);
        remoteRenderer = new SurfaceViewRenderer(this);
        localRenderer.setMirror(true);
        remoteRenderer.setMirror(true);
        localRenderer.init(EglUtils.getRootEglBase().getEglBaseContext(), null);
        remoteRenderer.init(EglUtils.getRootEglBase().getEglBaseContext(), null);

        rootLayout.addView(localRenderer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));
        rootLayout.addView(remoteRenderer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));

        setContentView(rootLayout);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        } else {
            bindWebRtcService();
        }
    }


    private void bindWebRtcService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.example.webrtcdemo", "com.example.webrtcdemo.messenger.service.WebRtcService"));
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindWebRtcService();
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRtcService != null) {
            try {
                webRtcService.unregisterCallback(callback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        unbindService(serviceConnection);
        localRenderer.release();
        remoteRenderer.release();
    }
}