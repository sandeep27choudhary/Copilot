# SQLCipher ships JNI bindings that are reached reflectively from native code.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
