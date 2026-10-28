# ── 通用属性 ────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# ── kotlinx.serialization ───────────────────────────────────
# 数据模型通过序列化器的反射查找工作，保留整个 data 包，避免混淆后找不到 Serializer。
-keep,includedescriptorclasses class com.filetransit.app.data.** { *; }
-keepclassmembers class com.filetransit.app.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    static **$* *;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# ── Retrofit / OkHttp ──────────────────────────────────────
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── 其他依赖 ───────────────────────────────────────────────
-dontwarn androidx.media3.**
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
