package io.github.nevesz.onehz.shizuku;

import android.os.IBinder;
import io.github.nevesz.onehz.shizuku.ITouchCallback;

interface IOneHzService {
    String listInputDevices();
    String dumpInputState();
    String foregroundPackage();
    boolean setDozeWhitelist(String packageName, boolean enabled);
    boolean isDozeWhitelisted(String packageName);
    void startTouchMonitoring(in String[] devicePaths, ITouchCallback callback);
    void stopTouchMonitoring();

    boolean writeSecureMode(String key, int value);
    int readSecureMode(String key);
    String readRefreshRateSetting(String key);
    boolean writeRefreshRateSetting(String key, String value);
    boolean deleteRefreshRateSetting(String key);
    boolean writeGlobalString(String key, String value);
    String readGlobalString(String key);
    String verifySettings();
    String serviceStats();
    String videoPlayback();

    boolean writeAodBrightness(int value);
    boolean clearAodBrightness();
    int readAodBrightness();

    void registerClient(IBinder client);
    void destroy();

    /** Video detection with user-tunable rules, encoded by VideoConfig. */
    String videoPlaybackWithConfig(String config);
}
