plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.starqcy.photocoach"
    compileSdk = 35
    defaultConfig { applicationId = "com.starqcy.photocoach"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
