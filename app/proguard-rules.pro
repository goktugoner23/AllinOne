# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep all data classes and models
-keep class com.example.allinone.data.** { *; }
-keep class com.example.allinone.feature.**.data.model.** { *; }

# Keep all ViewModels and related classes
-keep class com.example.allinone.viewmodels.** { *; }
-keep class com.example.allinone.feature.**.ui.viewmodel.** { *; }

# Keep all Fragments and Activities
-keep class com.example.allinone.ui.** { *; }
-keep class com.example.allinone.feature.**.ui.** { *; }

# Keep Firebase/Firestore classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep all classes with no-arg constructors (for Firebase deserialization)
-keepclassmembers class * {
    <init>();
}

# Keep all classes annotated with @Keep
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep all Serializable classes
-keep class * implements java.io.Serializable { *; }

# Keep all Parcelable classes
-keep class * implements android.os.Parcelable { *; }

# General Android rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson specific rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson TypeToken rules
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# Preserve Gson generic signatures
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all Instagram models (prevent Firebase deserialization issues)
-keep class com.example.allinone.feature.instagram.data.model.** { *; }

# Keep all classes used with reflection
-keep class com.example.allinone.feature.instagram.data.model.InstagramPost { *; }
-keep class com.example.allinone.feature.instagram.data.model.InstagramMetrics { *; }
-keep class com.example.allinone.feature.instagram.data.model.InstagramAccount { *; }
-keep class com.example.allinone.feature.instagram.data.model.InstagramAnalytics { *; }
-keep class com.example.allinone.feature.instagram.data.model.AnalyticsSummary { *; }
-keep class com.example.allinone.feature.instagram.data.model.SyncInfo { *; }
-keep class com.example.allinone.feature.instagram.data.model.ChatMessage { *; }
-keep class com.example.allinone.feature.instagram.data.model.AISource { *; }
-keep class com.example.allinone.feature.instagram.data.model.AIMetadata { *; }
-keep class com.example.allinone.feature.instagram.data.model.AISourceMetadata { *; }

# Keep all Task and TaskGroup classes
-keep class com.example.allinone.data.Task { *; }
-keep class com.example.allinone.data.TaskGroup { *; }

# Keep all Firebase repository classes
-keep class com.example.allinone.firebase.** { *; }

# Keep all adapters (to prevent issues with ViewHolder access)
-keep class com.example.allinone.adapters.** { *; }

# Hilt/Dagger rules
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class **_HiltComponents$* { *; }
-keep class **_*Factory { *; }
-keep class **_*Module { *; }

# SLF4J rules - suppress warnings for missing implementation classes
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
-keep class org.slf4j.** { *; }

# WebSocket library rules
-dontwarn org.java_websocket.**
-keep class org.java_websocket.** { *; }

# OkHttp and Retrofit rules
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }