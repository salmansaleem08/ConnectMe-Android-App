plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services) // ✅ Ensure this is included

}

android {
    namespace = "com.salmansaleem.i220904"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salmansaleem.i220904"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}



dependencies {

    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-database")  // ✅ Managed by BoM


    // Firebase Services
    implementation("com.google.firebase:firebase-auth")      // Firebase Auth
    implementation("com.google.firebase:firebase-database:21.0.0") // Realtime Database

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.firestore.ktx)
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")


    implementation("com.google.firebase:firebase-functions:20.4.0")
    implementation("com.google.firebase:firebase-messaging:23.4.1")


    implementation ("io.agora.rtc:full-sdk:4.2.6") // Use the latest version
    //implementation ("com.google.firebase:firebase-messaging:23.1.2")


     // If i include theses three i get many errors..

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.jsonwebtoken:jjwt:0.12.6")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}

apply(plugin = "com.google.gms.google-services") // 🔥 Important!