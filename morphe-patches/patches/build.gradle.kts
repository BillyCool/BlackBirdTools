group = "app.blackbird"

patches {
    about {
        name = "BlackBird Patches"
        description = "Redirects a Unity IL2CPP game to a custom server"
        source = "git@github.com:BillyCool/BlackBirdTools.git"
        author = "BillyCool"
        contact = "na"
        website = "na"
        license = "MIT"
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
    // Writes patches-list.json, which Morphe's web-search reads to find APKMirror download pages.
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }
}
