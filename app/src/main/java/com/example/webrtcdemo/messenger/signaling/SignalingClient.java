package com.example.webrtcdemo.messenger.signaling;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.SessionDescription;

public class SignalingClient {

    public interface Callback {
        void onOfferReceived(SessionDescription offer);
        void onAnswerReceived(SessionDescription answer);
        void onIceCandidateReceived(String sdpMid, int sdpMLineIndex, String candidate);
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void sendOffer(SessionDescription offer) {
        // 模拟信令传输
        Log.d("SignalingClient", "Sending Offer:\n" + offer.description);
        // 此处你可以替换为 WebSocket 实际发送逻辑
        if (callback != null) {
            callback.onOfferReceived(offer);
        }
    }

    public void sendAnswer(SessionDescription answer) {
        Log.d("SignalingClient", "Sending Answer:\n" + answer.description);
        if (callback != null) {
            callback.onAnswerReceived(answer);
        }
    }

    public void sendIceCandidate(String sdpMid, int sdpMLineIndex, String candidate) {
        Log.d("SignalingClient", "Sending Candidate:\n" + candidate);
        if (callback != null) {
            callback.onIceCandidateReceived(sdpMid, sdpMLineIndex, candidate);
        }
    }
}

