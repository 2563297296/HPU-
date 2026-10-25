# --- 混淆规则（R8 强化版） ---
# 保留 Android 清单引用的组件类名
-keep public class * extends android.app.Activity { public <init>(); }
-keep public class * extends android.app.Service { public <init>(); }
-keep public class * extends android.content.BroadcastReceiver { public <init>(); }
-keep public class * extends android.appwidget.AppWidgetProvider { public <init>(); }
-keep public class * extends android.app.job.JobService { public <init>(); }
-keep public class * extends android.app.Application { public <init>(); }

# 保留组件生命周期回调
-keepclassmembers public class * extends android.app.Activity {
    protected void onCreate(android.os.Bundle);
    protected void onResume();
    protected void onPause();
    protected void onStart();
    protected void onStop();
    protected void onDestroy();
    protected void onActivityResult(int, int, android.content.Intent);
    public void *(android.view.View);
}
-keepclassmembers public class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}
-keepclassmembers public class * extends android.app.job.JobService {
    public boolean onStartJob(android.app.job.JobParameters);
    public boolean onStopJob(android.app.job.JobParameters);
}
-keepclassmembers public class * extends android.appwidget.AppWidgetProvider {
    public void onUpdate(android.content.Context, android.appwidget.AppWidgetManager, int[]);
    public void onEnabled(android.content.Context);
    public void onDisabled(android.content.Context);
}

# 保留 R 资源类字段
-keepclassmembers class cn.edu.hpu.powerwidget.R$* {
    public static <fields>;
}

# --- 加强混淆 ---
-repackageclasses
-allowaccessmodification
-overloadaggressively
-mergeinterfacesaggressively
-optimizationpasses 5
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# 仅保留注解，剥离 SourceFile / LineNumberTable / Signature 等调试信息
-keepattributes *Annotation*

# 抑制告警
-dontwarn android.**
-dontwarn javax.**
-dontwarn java.**
-dontwarn org.**
