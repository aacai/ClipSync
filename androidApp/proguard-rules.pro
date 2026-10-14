# 这里只放「库自己没带、运行时又必须按名字找到」的规则。
# kotlinx-serialization / Room / Ktor / Coil 这些都在产物里自带 consumer 规则，
# AGP 会自动合并，手抄一遍只会把真正需要的两条埋掉。
# 想知道每个库到底给了什么，看构建产物里合并后的全量规则：
#   app/build/outputs/mapping/release/configuration.txt   （按来源分段，带路径）
#   app/build/outputs/mapping/release/seeds.txt           （最终被 -keep 命中的类与成员）

# ---- 崩溃堆栈可读 ----
# AGP 默认那份 proguard-android-optimize.txt 不带行号，补上；
# 源码文件名统一改名，免得把开发机路径带进产物。反混淆用同目录下的 mapping.txt。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx-serialization ----
# 库自带规则只锁 serializer() 入口和 Companion 字段，生成的 $$serializer 类
# 自身成员照样会被改名/裁掉；一旦缺了 serialize/deserialize 的 Object 桥接方法，
# 运行时反射拿到序列化器再调用就崩，且只在 release 复现。
# 故意不按包名限定：任何模块的 @Serializable 都需要，这份文件可以直接抄。
-keepclassmembers class **$$serializer { *; }
