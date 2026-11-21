# Keep AIDL stubs, the UserService and Shizuku provider: they are instantiated by name.
-keep class io.github.nevesz.onehz.shizuku.** { *; }
-keep class io.github.nevesz.onehz.engine.OneHzKeepAliveService { *; }
-keep class rikka.shizuku.** { *; }

# AIDL / Binder plumbing
-keep class * extends android.os.Binder { *; }
-keep class * implements android.os.IInterface { *; }
-keepclassmembers class * implements android.os.IInterface {
    public static ** asInterface(android.os.IBinder);
    public static ** Stub;
}

# Reflection-friendly metadata
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku / hidden framework APIs are called through reflection and string names.
-dontwarn com.samsung.android.**
-dontwarn android.hardware.display.**
-dontwarn android.os.ServiceManager
