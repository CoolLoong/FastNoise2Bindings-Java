plugins {
    id("java")
    id("maven-publish")
}

group = "org.github.fastnoise"
version = "0.0.3"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(23)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.wrapper {
    gradleVersion = "8.10.1"
}

tasks.jar {
    manifest {
        attributes(
            "Enable-Native-Access" to "ALL-UNNAMED"
        )
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("FastNoise2Bindings-Java")
                description.set("Bindings for FastNoise2 in Java.")
                url.set("https://github.com/CoolLoong/FastNoise2Bindings-Java")

                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("CoolLoong")
                        name.set("CoolLoong")
                        email.set("wingdon@foxmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git:/github.com/CoolLoong/FastNoise2Bindings-Java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/CoolLoong/FastNoise2Bindings-Java.git")
                    url.set("https://github.com/CoolLoong/FastNoise2Bindings-Java")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
    }
}