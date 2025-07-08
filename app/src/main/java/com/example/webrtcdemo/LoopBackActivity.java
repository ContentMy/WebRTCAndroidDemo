package com.example.webrtcdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

/**
 * WebRTC 本地回环测试（Local Loopback），模拟了真实的 WebRTC 通话流程，包括 PeerConnection 协商、ICE 候选交换、视频轨道传输 等核心流程
 *
 * 核心流程
 * (1) 初始化阶段
 * 权限检查：请求摄像头和麦克风权限（和之前相同）。
 * WebRTC 初始化：
 * 创建 PeerConnectionFactory（带硬件编解码支持）。
 * 初始化本地摄像头捕获，创建 VideoTrack 并绑定到 localView。
 *
 * (2) 创建两个 PeerConnection
 * pc1 和 pc2：模拟本地和远程两端。
 * pc1 配置为 发送视频（SEND_ONLY）。
 * pc2 配置为 接收视频（RECV_ONLY）。
 * ICE 服务器：使用 Google 的公共 STUN 服务器（stun:stun.l.google.com:19302）。
 *
 * (3) 模拟 Offer/Answer 协商
 * pc1 创建 Offer：
 * 生成 SDP 描述（包含本地媒体能力）。
 * 设置 pc1 的本地描述（Local Description）。
 * 将 Offer 传给 pc2 作为远程描述（Remote Description）。
 * pc2 创建 Answer：
 * 生成应答 SDP。
 * 设置 pc2 的本地描述，并传回 pc1 作为远程描述。
 *
 * (4) ICE 候选交换
 * 通过 PeerObserver 监听 ICE 候选：
 * 当 pc1 发现 ICE 候选时，自动添加到 pc2。
 * 当 pc2 发现 ICE 候选时，自动添加到 pc1。
 *
 * (5) 视频回环显示
 * pc2 接收到远程视频轨道（实际是 pc1 发送的本地视频）：
 * 通过 onTrack 回调，将视频渲染到 remoteView。
 */
public class LoopBackActivity extends AppCompatActivity {

    private static final String TAG = "WebRTC-Loopback";

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;
    private PeerConnectionFactory factory;
    private PeerConnection pc1;
    private PeerConnection pc2;
    private EglBase eglBase;
    private VideoCapturer capturer;
    private VideoTrack localVideoTrack;
    private VideoTrack dummyTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loop_back);

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

        initPeerConnectionFactory();
        startCamera();
        createPeerConnections();
        startCall();
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
                                true))
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    private void startCamera() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        String frontCamera = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                frontCamera = name;
                break;
            }
        }
        if (frontCamera == null) {
            Log.e(TAG, "No front camera found");
            return;
        }

        capturer = enumerator.createCapturer(frontCamera, null);
        if (capturer == null) {
            Log.e(TAG, "Failed to create camera capturer");
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(false);
        capturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        capturer.startCapture(640, 480, 30);

        localVideoTrack = factory.createVideoTrack("localTrack", videoSource);
        localVideoTrack.addSink(localView);
    }

    private void createPeerConnections() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        pc1 = factory.createPeerConnection(config, new PeerObserver("pc1"));
        pc2 = factory.createPeerConnection(config, new PeerObserver("pc2"));

        pc1.addTransceiver(localVideoTrack,
                new RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));

        pc2.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                new RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
    }

    private void startCall() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        pc1.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "pc1 offer created");

                pc1.setLocalDescription(new SimpleSdpObserver(), sdp);
                pc2.setRemoteDescription(new SimpleSdpObserver(), sdp);

                pc2.createAnswer(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdpAnswer) {
                        Log.d(TAG, "pc2 answer created");

                        pc2.setLocalDescription(new SimpleSdpObserver(), sdpAnswer);
                        pc1.setRemoteDescription(new SimpleSdpObserver(), sdpAnswer);
                    }

                    @Override
                    public void onSetSuccess() {
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "createAnswer failed: " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                    }
                }, constraints);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "createOffer failed: " + s);
            }

            @Override
            public void onSetFailure(String s) {
            }
        }, constraints);
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }
    }

    private class PeerObserver implements PeerConnection.Observer {

        private final String tag;

        PeerObserver(String tag) {
            this.tag = tag;
        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            if (tag.equals("pc1")) {
                pc2.addIceCandidate(candidate);
            } else {
                pc1.addIceCandidate(candidate);
            }
        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {
            MediaStreamTrack track = transceiver.getReceiver().track();
            if (track instanceof VideoTrack) {
                Log.d(TAG, tag + " received remote video track");
                runOnUiThread(() -> {
                    ((VideoTrack) track).addSink(remoteView);
                });
            }
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d(TAG, tag + " ICE state = " + newState);
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
        }

        @Override
        public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        }
    }

    @Override
    protected void onDestroy() {
        if (pc1 != null) pc1.close();
        if (pc2 != null) pc2.close();
        if (capturer != null) {
            try {
                capturer.stopCapture();
                capturer.dispose();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop capturer", e);
            }
        }
        if (eglBase != null) eglBase.release();
        super.onDestroy();
    }
}
