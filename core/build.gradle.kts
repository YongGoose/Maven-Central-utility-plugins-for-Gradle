plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("pomUtils") {
            id = "io.github.YongGoose.pom-utils"
            implementationClass = "io.github.YongGoose.PomUtilsPlugin"
        }
    }
}