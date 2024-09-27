# FastNoise2-Java Bindings

Java bindings for [FastNoise2](https://github.com/Auburn/FastNoise2) noise generation library

Uses the metadata system in FastNoise2 to reference node types and variable names, this means the bindings don't need to
updated when there are new/changed nodes/variables in FastNoise2

[Example usage](https://github.com/Auburn/FastNoise2Bindings/blob/master/src/test/java/com/github/fastnoise/FastNoiseTest.java)

## Usage

You can add this library to your project using JitPack.

### Gradle (Kotlin DSL)

In your `build.gradle.kts` file, add the following code:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.CoolLoong:FastNoise2Bindings-Java:0.0.1")
}
```

### Maven
```
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.CoolLoong</groupId>
        <artifactId>FastNoise2Bindings-Java</artifactId>
        <version>0.0.1</version>
    </dependency>
</dependencies>
```