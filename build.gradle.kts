plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.wrapper{
    gradleVersion = "8.7"
}

tasks.compileJava{
    options.compilerArgs.add("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
}