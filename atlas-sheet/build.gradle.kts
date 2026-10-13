plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish")
}
android {
    namespace = "dev.atlas.sheet"
    compileSdk = 35
    defaultConfig { minSdk = 24; consumerProguardFiles("consumer-rules.pro") }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(platform("androidx.compose:compose-bom:2025.06.01"))
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    api("androidx.fragment:fragment-ktx:1.8.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
mavenPublishing {
    // Coordinates and version come from gradle.properties (GROUP, POM_ARTIFACT_ID, VERSION_NAME).
    publishToMavenCentral(automaticRelease = false)
    // Signing is required for Maven Central and skipped when no key is configured (local builds).
    if (!providers.gradleProperty("signingInMemoryKey").orNull.isNullOrBlank()) signAllPublications()
    pom {
        name.set("Atlas Sheet")
        description.set("Detent-driven Jetpack Compose bottom sheets with sticky chrome, live content fit and IME policies")
        inceptionYear.set("2026")
        url.set("https://github.com/andrew-levy/atlas-sheet")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("andrew-levy")
                name.set("Andrew Levy")
                url.set("https://github.com/andrew-levy")
            }
        }
        scm {
            url.set("https://github.com/andrew-levy/atlas-sheet")
            connection.set("scm:git:https://github.com/andrew-levy/atlas-sheet.git")
            developerConnection.set("scm:git:ssh://git@github.com/andrew-levy/atlas-sheet.git")
        }
    }
}
