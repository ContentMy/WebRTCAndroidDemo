// IWebRtcCallback.aidl
package com.example.webrtcdemo.binder;

interface IWebRtcCallback {
    void onLocalVideoTrackCreated(String trackId);
    void onRemoteVideoTrackCreated(String trackId);
}