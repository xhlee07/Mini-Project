plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ctfly.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ctfly.staff"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
        manifestPlaceholders["launcherIcon"] = "@drawable/ic_staff_launcher"
        resValue("string", "app_name", "Staff CTFLY")
        buildConfigField("String", "PAYMENT_MODE", "\"simulation\"")
        buildConfigField("boolean", "STAFF_APP", "true")
        buildConfigField("String", "APP_NAME", "\"Staff CTFLY\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://shfatnjwpdyxdbehvjil.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_Pf2aTO3MNDEdo_6AAhEbfQ_7xzpF17d\"")
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("../app/src/main/AndroidManifest.xml")
            java.setSrcDirs(listOf("../app/src/main/kotlin"))
            res.setSrcDirs(listOf("../app/src/main/res"))
            assets.srcDir("../app/src/main/assets-ui")
        }
        getByName("test").java.setSrcDirs(listOf("../app/src/test"))
        getByName("androidTest") {
            java.setSrcDirs(listOf("../app/src/androidTest/kotlin"))
            assets.setSrcDirs(listOf("../app/src/androidTest/assets"))
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

