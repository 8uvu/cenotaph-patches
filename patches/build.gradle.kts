group = "app.epitaph"

patches {
    about {
        name = "Cenotaph"
        description = "Cenotaph: standalone Morphe patches for Instagram, rebuilt from scratch. Four patches: runtime, settings app, the DM thread anchor and chat wallpaper, which replaces the bitmap in Instagram's own thread background setter. Chat wallpaper registers through the required \"Cenotaph - Thread anchor\" patch — keep it enabled. Targets IG 447.0.0.55.81 (385311895) and 439.0.0.37.89."
        source = "https://github.com/8uvu/cenotaph-patches"
        author = "Epitaph"
        contact = "https://github.com/8uvu/cenotaph-patches/issues"
        website = "https://github.com/8uvu/cenotaph-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
