package com.example.webrtcdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.Camera2Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/**
 * 1. 权限请求
 * 检查并请求CAMERA和RECORD_AUDIO权限
 * 在权限授予后初始化WebRTC
 *
 * 2. WebRTC初始化
 * 创建两个SurfaceViewRenderer视图（local_view和remote_view）
 * 初始化EGL上下文（用于OpenGL渲染）
 * 设置视图镜像（remote_view设置为镜像模式）
 *
 * 3. PeerConnectionFactory初始化
 * 初始化WebRTC核心工厂类
 * 配置视频编码器/解码器工厂（支持硬件加速）
 *
 * 4. 摄像头捕获和视频流处理
 * 使用Camera2 API枚举设备
 * 查找并创建前置摄像头捕获器
 * 初始化视频源和SurfaceTextureHelper
 * 开始捕获视频（640x480分辨率，30fps）
 *
 * 5. 视频回环实现
 * 创建本地视频轨道
 * 关键部分：将同一视频轨道同时添加到两个视图渲染器
 * localVideoTrack.addSink(localView) → 显示本地视图
 * localVideoTrack.addSink(remoteView) → 显示"远程"视图（实际是本地视频的镜像）
 *
 * 6. 资源清理
 * 停止和释放摄像头捕获器
 * 释放PeerConnectionFactory和EGL资源
 *
 * 技术亮点
 * 视频回环：通过将同一视频源绑定到两个渲染器，模拟了本地+远程的视频通话效果
 * 硬件加速：使用DefaultVideoEncoderFactory启用了硬件编解码
 * 镜像处理：远程视图设置了setMirror(true)使其显示为镜像效果
 *
 * 注意点
 * 这只是一个演示实现，不是真正的WebRTC通话（没有信令交换或网络传输）
 *
 */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WebRTC-Loopback";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;
    private PeerConnectionFactory factory;
    private EglBase eglBase;
    private VideoCapturer capturer;
    private VideoTrack localVideoTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initWebRTC();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                initWebRTC();
            } else {
                Log.e(TAG, "Camera and microphone permissions denied");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initWebRTC() {
        localView = findViewById(R.id.local_view);
        remoteView = findViewById(R.id.remote_view);

        eglBase = EglBase.create();

        localView.init(eglBase.getEglBaseContext(), null);
        remoteView.init(eglBase.getEglBaseContext(), null);
        remoteView.setMirror(true);//设置了镜像

        initPeerConnectionFactory();
        startCamera();
    }

    private void initPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(
                                eglBase.getEglBaseContext(),
                                true,
                                true
                        ))
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    private void startCamera() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        String frontCameraName = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                frontCameraName = name;
                break;
            }
        }
        if (frontCameraName == null) {
            Log.e(TAG, "No front camera found");
            return;
        }

        capturer = enumerator.createCapturer(frontCameraName, null);
        if (capturer == null) {
            Log.e(TAG, "Failed to create camera capturer");
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(false);
        capturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        capturer.startCapture(640, 480, 30);

        localVideoTrack = factory.createVideoTrack("localVideoTrack", videoSource);

        // ✅ Loopback: add sink to both views
        localVideoTrack.addSink(localView);
        localVideoTrack.addSink(remoteView);

        Log.d(TAG, "Loopback started: video track feeding both views.");
    }

    @Override
    protected void onDestroy() {
        if (capturer != null) {
            try {
                capturer.stopCapture();
                capturer.dispose();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera", e);
            }
        }
        if (factory != null) factory.dispose();
        if (eglBase != null) eglBase.release();
        super.onDestroy();
    }
}
