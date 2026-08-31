// Top-level build file: declares which plugin versions submodules can use.
// "apply false" means the root project itself doesn't apply them, only app/build.gradle.kts does.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
