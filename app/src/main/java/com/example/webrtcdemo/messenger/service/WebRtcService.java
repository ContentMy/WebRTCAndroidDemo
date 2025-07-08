package com.example.webrtcdemo.messenger.service;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.example.webrtcdemo.binder.IWebRtcCallback;
import com.example.webrtcdemo.binder.IWebRtcService;
import com.example.webrtcdemo.messenger.model.PeerConnectionObserver;
import com.example.webrtcdemo.messenger.model.SdpObserverAdapter;
import com.example.webrtcdemo.messenger.utils.EglUtils;
import com.example.webrtcdemo.messenger.utils.WebRtcHolder;
import org.webrtc.*;

import java.util.ArrayList;

public class WebRtcService extends Service {

    private static final String TAG = "WebRtcService";

    private PeerConnectionFactory factory;
    private VideoTrack localVideoTrack;
    private IWebRtcCallback callback;

    private PeerConnection localPeer;
    private PeerConnection remotePeer;

    private final IWebRtcService.Stub binder = new IWebRtcService.Stub() {
        @Override
        public void registerCallback(IWebRtcCallback cb) {
            callback = cb;
        }

        @Override
        public void unregisterCallback(IWebRtcCallback cb) {
            callback = null;
        }

        @Override
        public void startCall() {
            initWebRTC();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void initWebRTC() {
        Log.d(TAG, "Initializing WebRTC");

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .createInitializationOptions()
        );

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                        new org.webrtc.DefaultVideoEncoderFactory(
                                EglUtils.getRootEglBase().getEglBaseContext(), true, true
                        )
                )
                .setVideoDecoderFactory(
                        new org.webrtc.DefaultVideoDecoderFactory(
                                EglUtils.getRootEglBase().getEglBaseContext()
                        )
                )
                .createPeerConnectionFactory();

        VideoCapturer capturer = createCameraCapturer();
        if (capturer == null) {
            Log.e(TAG, "No camera capturer found.");
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", EglUtils.getRootEglBase().getEglBaseContext()
        );

        VideoSource videoSource = factory.createVideoSource(false);
        capturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        try {
            capturer.startCapture(640, 480, 30);
            Log.d(TAG, "Camera started capturing...");
        } catch (Exception e) {
            Log.e(TAG, "startCapture failed", e);
        }

        localVideoTrack = factory.createVideoTrack("localTrack", videoSource);

        localVideoTrack.addSink(frame -> {
            Log.d(TAG, "Local video frame arrived: "
                    + frame.getRotatedWidth() + "x" + frame.getRotatedHeight());
        });


        // 模拟远端 VideoTrack
        VideoTrack remoteVideoTrack = factory.createVideoTrack("remoteTrack", videoSource);
        WebRtcHolder.putVideoTrack("remoteTrack", remoteVideoTrack);

        WebRtcHolder.putVideoTrack("localTrack", localVideoTrack);

        if (callback != null) {
            try {
                callback.onLocalVideoTrackCreated("localTrack");
                callback.onRemoteVideoTrackCreated("remoteTrack");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initWebRTCNew() {
        // 同前：初始化 factory、capturer、localVideoTrack

        // 1. 创建 PeerConnection（local + remote）
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList<>());
        localPeer = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                // 本地 candidate 转发给 remote
                remotePeer.addIceCandidate(candidate);
            }

            @Override
            public void onAddStream(MediaStream stream) {
                Log.d(TAG, "Local onAddStream called");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }
        });

        remotePeer = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                // 远端 candidate 转发给 local
                localPeer.addIceCandidate(candidate);
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                VideoTrack remoteTrack = (VideoTrack) receiver.track();
                if (callback != null) {
                    WebRtcHolder.putVideoTrack("remoteTrack", remoteTrack);
                    try {
                        callback.onRemoteVideoTrackCreated("remoteTrack");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // 2. 添加本地流
        MediaStream stream = factory.createLocalMediaStream("localStream");
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);

        // 3. Create offer from local
        localPeer.createOffer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                localPeer.setLocalDescription(new SdpObserverAdapter(), offer);
                remotePeer.setRemoteDescription(new SdpObserverAdapter(), offer);

                // remote 端创建 answer
                remotePeer.createAnswer(new SdpObserverAdapter() {
                    @Override
                    public void onCreateSuccess(SessionDescription answer) {
                        remotePeer.setLocalDescription(new SdpObserverAdapter(), answer);
                        localPeer.setRemoteDescription(new SdpObserverAdapter(), answer);
                    }
                }, new MediaConstraints());
            }
        }, new MediaConstraints());
    }



    private VideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        // fallback back camera
        for (String deviceName : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }
}