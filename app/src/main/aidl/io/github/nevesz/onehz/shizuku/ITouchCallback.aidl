package io.github.nevesz.onehz.shizuku;

oneway interface ITouchCallback {
    // action: 0 = DOWN (first finger of this device)
    //         1 = UP (last finger of this device)
    //         2 = STOPPED (remote getevent died; re-discover the device)
    void onTouch(String devicePath, int action, int activePointers);
}
