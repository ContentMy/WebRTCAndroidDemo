// IWebRtcService.aidl
package com.example.webrtcdemo.binder;
import com.example.webrtcdemo.binder.IWebRtcCallback;

interface IWebRtcService {
    void registerCallback(IWebRtcCallback callback);
    void unregisterCallback(IWebRtcCallback callback);
    void startCall();
}