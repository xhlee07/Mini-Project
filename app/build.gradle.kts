import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val pushConfig = file("google-services.json").takeIf { it.exists() }?.let {
    JsonSlurper().parse(it) as Map<*, *>
}
val pushProject = pushConfig?.get("project_info") as? Map<*, *>
val pushClient = (pushConfig?.get("client") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull {
    val info = it["client_info"] as? Map<*, *>
    (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.ctfly.app"
}
check(pushConfig == null || pushClient != null) { "Firebase configuration must include com.ctfly.app" }
android {
    namespace = "com.ctfly.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ctfly.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "1.24"
        buildConfigField("boolean", "PUSH_CONFIGURED", (pushClient != null).toString())
        if (pushClient != null) {
            val info = pushClient["client_info"] as Map<*, *>
            val key = ((pushClient["api_key"] as List<*>).first() as Map<*, *>)["current_key"].toString()
            resValue("string", "google_app_id", info["mobilesdk_app_id"].toString())
            resValue("string", "google_api_key", key)
            resValue("string", "gcm_defaultSenderId", pushProject!!["project_number"].toString())
            resValue("string", "project_id", pushProject["project_id"].toString())
        }
        manifestPlaceholders["launcherIcon"] = "@drawable/ic_launcher"
        resValue("string", "app_name", "CTFLY Gaming Cafe")
        buildConfigField("String", "PAYMENT_MODE", "\"simulation\"")
        buildConfigField("boolean", "STAFF_APP", "false")
        buildConfigField("String", "APP_NAME", "\"CTFLY Gaming Cafe\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://shfatnjwpdyxdbehvjil.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_Pf2aTO3MNDEdo_6AAhEbfQ_7xzpF17d\"")
    }
    sourceSets.getByName("main").assets.srcDir("src/main/assets-ui")
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
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.firebase:firebase-messaging:25.1.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// Keep detailed procedural mesh construction off the phone's loading path.
tasks.register<JavaExec>("bakeTourMesh") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath = files("build/tmp/kotlin-classes/debug", "build/tmp/kotlin-classes/debugUnitTest") + configurations.getByName("debugUnitTestRuntimeClasspath")
    mainClass.set("com.ctfly.app.TourMeshBaker")
    args(file("src/main/assets/tour").absolutePath)
}
