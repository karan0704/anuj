/**
 * Tests about the code itself: they read the Kotlin sources of every module
 * and fail when one of the project's architecture rules is broken. There is
 * no production code here.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
}

/** The sources of the other modules are the input, so a change anywhere must re-run these tests. */
tasks.test {
    inputs.files(
        fileTree(rootDir) {
            include("**/src/main/kotlin/**/*.kt")
            /** Build folders hold generated code that other tasks are still writing. */
            exclude("**/build/**")
        },
    ).withPropertyName("checkedSources")
}
