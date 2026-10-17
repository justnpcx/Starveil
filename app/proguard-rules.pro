# Starveil ProGuard rules.
#
# The rule of thumb here: anything the Go side calls by name has to survive,
# because a renamed method is not a build error — it is a crash on the first
# connection, or worse, a silently dead callback.

# gomobile's binding. Every method on these types is reached from Go through
# JNI by name, and `Libbox` is the entry point to the whole kernel.
-keep class io.nekohasekai.libbox.** { *; }

# Our implementations of the Go interfaces (platform callbacks, the command
# server handler). Go holds them as interface values and calls the methods by
# their Java names.
-keep class com.justnpcx.starveil.core.kernel.libbox.** { *; }

# Hilt-generated code.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# SQLCipher loads its native library and calls back into Java.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.** { *; }

# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Kotlin metadata, so the generated code can still read generic signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# The VPN service is instantiated by the system from the manifest.
-keep class com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService { *; }
