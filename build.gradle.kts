import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinTest


version = "0.0"


plugins {
    kotlin("multiplatform") version "1.4.32"
}


repositories {
    mavenCentral()
}


kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")

    when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}


val compileKotlinNative: KotlinNativeCompile by tasks
compileKotlinNative.kotlinOptions.freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")


tasks.withType<KotlinTest> {
    testLogging {
        // always rerun tests
        outputs.upToDateWhen { false }

        events("skipped", "failed")

        // https://github.com/gradle/gradle/issues/5431
        // https://github.com/gradle/kotlin-dsl-samples/issues/836#issuecomment-384206237
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                // print only the bottom-level test result information
                if (suite.className == null) return

                val details = if (result.skippedTestCount > 0 || result.failedTestCount > 0) {
                    ": ${result.successfulTestCount} successes, " +
                            "${result.failedTestCount} failures, " +
                            "${result.skippedTestCount} skipped"
                } else {
                    ""
                }

                println("${suite.displayName}: ${result.resultType} " +
                        "(${result.testCount} tests$details)")
            }
        })
    }
}
