package com.example.webrtcdemo.messenger.utils;

import org.webrtc.EglBase;

/**
 * @Author ContentMy
 * @Date 2025/7/8 14:13
 * @Description
 */
public class EglUtils {
    private static EglBase rootEglBase;

    public static EglBase getRootEglBase() {
        if (rootEglBase == null) {
            rootEglBase = EglBase.create();
        }
        return rootEglBase;
    }
}
