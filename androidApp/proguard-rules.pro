# kotlinx-serialization 的 consumer 规则 AGP 会自动合并；这条把 $$serializer 整类成员锁住，
# 缺了它 R8 会裁掉 envelope/payload 序列器的 Object 桥接方法，线上解到即崩。
-keepclassmembers class zhiqiu.app.cs.**$$serializer { *; }
