plugins {
    id("com.android.application")
}

android {
    namespace = "cn.edu.hpu.powerwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.edu.hpu.powerwidget"
        minSdk = 23
        targetSdk = 35
        versionCode = 28
        versionName = "2.2.22"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
