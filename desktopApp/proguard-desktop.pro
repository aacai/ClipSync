# 桌面端 ProGuard 的补充规则（模板：新项目直接抄这份，再按自己的依赖补）。
# Android 那边由 AGP 自动合并各库 AAR 里的 consumer 规则；桌面这条链路只吃
# 插件自带的 default-compose-desktop-rules.pro（kotlin/skia/skiko/coroutines），
# 库自己的规则一个都不认，所以反射入口要在这里点名。
# 只有 createReleaseDistributable / packageRelease* 这条任务链会过 ProGuard，
# 跑 debug 的 packageDmg 时这份文件根本不参与，别拿 debug 包的现象推断 release 包。

# okio 的扩展函数带 @JvmName 后缀（buffer$5ae116e 这种），ProGuard 的内联/特化改写
# 会把返回类型收窄成实现类，字节码校验当场 VerifyError。整包留着最省事，也就几百 K。
-keep class okio.** { *; }

# 库对着别的 skiko / 可选依赖编出来的引用，ProGuard 一律当错误拦下来，运行时走不到就没影响。
# backdrop 是对着旧版 skiko 编的（ColorMatrix、ColorFilter.makeMatrix、RuntimeShaderBuilder.makeShader 签名都动过）；
# cryptography-provider-jdk 里的 BouncyCastleBridge 是可选适配，本工程没引 BC。
-dontwarn com.kashif_e.backdrop.**
-dontwarn org.bouncycastle.**

# ProGuard 的静态图不认 JNI 绑定：native 方法在 Java 侧没人显式调用就会被删，
# 运行时按符号名去找就 UnsatisfiedLinkError（实测踩过 BundledSQLiteDriverKt.nativeThreadSafeMode）。
-keepclasseswithmembers class * {
    native <methods>;
}

# ProGuard 不读 META-INF/services，ServiceLoader 找的实现类在静态图里没有引用，会被整类删掉。
# 按实现接口点名比整包 keep 省得多，新接一个引擎/序列化扩展就再加一条。
# 漏一条不会编译报错，运行时才炸，所以改完依赖记得核对：包里 META-INF/services 列的类必须都还在。
-keep class * implements io.ktor.client.HttpClientEngineContainer {
    <init>();
}
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    <init>();
}
-keep class * implements dev.whyoleg.cryptography.CryptographyProviderContainer {
    <init>();
}

# 无参 HttpClient {} 靠按名字试探引擎，Java 引擎只在 jvm 侧被反射加载。
-keep class io.ktor.client.engine.java.** {
    <init>();
    <fields>;
}

# reified 的 encodeToString/decodeFromString 走 Class.getDeclaredField("Companion")
# + serializer()，序列化器和这两个入口不能被裁。
-keepclassmembers class **$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
}

# 其余按库补的口子，用到什么加什么（本仓库没用到，留个清单免得下次重新踩）：
# - Room：Class.forName("<Database>_Impl") 找生成实现，静态图里没人引用它。
#   -if @androidx.room3.Database class ** { *; }  /  -keep class <1>_Impl { <init>(...); }
# - JDBC：DriverManager 反射实例化驱动，-keep class * implements java.sql.Driver { <init>(); }
# - Coil：FetcherServiceLoaderTarget 的实现类同样从 META-INF/services 里读。
