package io.github.nevesz.onehz.shizuku;

/** Foreground package changes reported by the privileged service. */
oneway interface IForegroundCallback {
    void onForegroundPackage(String packageName);
}
