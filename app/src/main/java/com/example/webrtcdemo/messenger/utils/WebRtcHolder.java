package com.example.webrtcdemo.messenger.utils;

import org.webrtc.VideoTrack;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author ContentMy
 * @Date 2025/7/8 14:58
 * @Description
 */
public class WebRtcHolder {
    private static final ConcurrentHashMap<String, VideoTrack> videoTrackMap = new ConcurrentHashMap<>();

    public static void putVideoTrack(String key, VideoTrack track) {
        videoTrackMap.put(key, track);
    }

    public static VideoTrack getVideoTrack(String key) {
        return videoTrackMap.get(key);
    }
}
