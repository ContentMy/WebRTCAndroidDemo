package com.example.webrtcdemo.messenger.service;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.example.webrtcdemo.binder.IWebRtcCallback;
import com.example.webrtcdemo.binder.IWebRtcService;
import com.example.webrtcdemo.messenger.model.PeerConnectionObserver;
import com.example.webrtcdemo.messenger.model.SdpObserverAdapter;
import com.example.webrtcdemo.messenger.signaling.SignalingClient;
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
//            initWebRTC();
            initWebRTCNew();
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
        Log.d(TAG, "Initializing WebRTC (New)");

        // 1. 初始化 PeerConnectionFactory（跟之前一样）
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .createInitializationOptions()
        );

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(
                                EglUtils.getRootEglBase().getEglBaseContext(),
                                true,
                                true
                        )
                )
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(
                                EglUtils.getRootEglBase().getEglBaseContext()
                        )
                )
                .createPeerConnectionFactory();

        // 2. 视频采集
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

        // 存储本地 Track
        WebRtcHolder.putVideoTrack("localTrack", localVideoTrack);
        if (callback != null) {
            try {
                callback.onLocalVideoTrackCreated("localTrack");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3. Signaling Client
        SignalingClient signalingClient = new SignalingClient();

        // 4. 创建 PeerConnections
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList<>());

        localPeer = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                // 通过 signaling 转发给 remote
                signalingClient.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }
        });

        remotePeer = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                signalingClient.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "remotePeer onAddTrack");
                if (receiver.track() instanceof VideoTrack) {
                    VideoTrack remoteTrack = (VideoTrack) receiver.track();
                    WebRtcHolder.putVideoTrack("remoteTrack", remoteTrack);
                    if (callback != null) {
                        try {
                            callback.onRemoteVideoTrackCreated("remoteTrack");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        // 5. 注册 Signaling 回调
        signalingClient.setCallback(new SignalingClient.Callback() {
            @Override
            public void onOfferReceived(SessionDescription offer) {
                Log.d(TAG, "onOfferReceived");

                remotePeer.setRemoteDescription(new SdpObserverAdapter(), offer);

                remotePeer.createAnswer(new SdpObserverAdapter() {
                    @Override
                    public void onCreateSuccess(SessionDescription answer) {
                        remotePeer.setLocalDescription(new SdpObserverAdapter(), answer);
                        signalingClient.sendAnswer(answer);
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onAnswerReceived(SessionDescription answer) {
                Log.d(TAG, "onAnswerReceived");
                localPeer.setRemoteDescription(new SdpObserverAdapter(), answer);
            }

            @Override
            public void onIceCandidateReceived(String sdpMid, int sdpMLineIndex, String candidate) {
                IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
                if (sdpMid != null && sdpMid.contains("video")) {
                    // 随便判断一下 local/remote
                    remotePeer.addIceCandidate(iceCandidate);
                } else {
                    localPeer.addIceCandidate(iceCandidate);
                }
            }
        });

        // 6. local 添加本地流
        MediaStream stream = factory.createLocalMediaStream("localStream");
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);

        // 7. local 创建 offer
        localPeer.createOffer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                Log.d(TAG, "local createOffer success");
                localPeer.setLocalDescription(new SdpObserverAdapter(), offer);
                signalingClient.sendOffer(offer);
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