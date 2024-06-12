# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization is turned off by default. Dex does not like code run
# through the ProGuard optimize and preverify steps (and performs some
# of these optimizations on its own).
-dontoptimize
-dontpreverify
# Note that if you want to enable optimization, you cannot just
# include optimization flags in your own project configuration file;
# instead you will need to point to the
# "proguard-android-optimize.txt" file instead of this one from your
# project.properties file.

-keepattributes *Annotation*
-keepattributes Exceptions, Signature, InnerClasses, EnclosingMethod, LineNumberTable, SourceFile

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}

# keep setters in Views so that animations can still work.
# see http://proguard.sourceforge.net/manual/examples.html#beans
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

#-keepclassmembers class **.R$* {
#    public static <fields>;
#}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

#-dontwarn org.apache.log4j.**

-dontshrink
-verbose

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep public class * extends java.lang.Exception

-keep class **.R { *; }
-keep class **.R$* { *; }


-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

#########################
# Wallet rules
#########################
-keep class com.pingidentity.sdk.pingonewallet.** { *; }
-dontwarn com.pingidentity.sdk.pingonewallet.**
-keep class com.pingidentity.did.** { *; }
-dontwarn com.pingidentity.did.**
#Did SDK
-keep class org.slf4j.impl.** { *; }
-dontwarn org.slf4j.impl.**
-keep class com.squareup.retrofit2.** { *; }
-dontwarn com.squareup.retrofit2.**
-keep class com.squareup.okhttp3.** { *; }
-dontwarn com.squareup.okhttp3.**
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class com.google.code.gson.** { *; }
-dontwarn com.google.code.gson.**
-keep class org.joda.time.** { *; }
-dontwarn org.joda.time.**
#Jose4j
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**
#Encrypted Shared Preferences
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.naming.**
#########################
#########################


-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

-keep public class com.google.android.gms.* { public *; }
-dontwarn com.google.android.gms.**
