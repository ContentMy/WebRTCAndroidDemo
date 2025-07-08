package com.example.webrtcdemo.messenger.model;

import org.webrtc.PeerConnection;

public abstract class PeerConnectionObserver implements PeerConnection.Observer {
    @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
    @Override public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {}
    @Override public void onIceConnectionReceivingChange(boolean receiving) {}
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {}
    @Override public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) {}
    @Override public void onAddStream(org.webrtc.MediaStream stream) {}
    @Override public void onRemoveStream(org.webrtc.MediaStream stream) {}
    @Override public void onRenegotiationNeeded() {}
    @Override public void onAddTrack(org.webrtc.RtpReceiver receiver, org.webrtc.MediaStream[] mediaStreams) {}
}