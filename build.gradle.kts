group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {//与dependencies块中的intellijPlatform块结合使用
        defaultRepositories()
    }
}

plugins {
    // A Gradle Core plugin: Provides support for building Java projects. Core plugins must be specified without a version number。
    // The plugin adds many dependency configurations, such as 'implementation', 'testImplementation' and so on for dependencies,
    //   https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_plugin_and_dependency_management。
    id("java")
    alias(libs.plugins.kotlin) // Kotlin support；也可使用【kotlin("jvm")】声明(settings.gradle.kts中定义好了版本)，一种Kotlin DSL的扩展写法
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin；也可使用常规方式【id("org.jetbrains.intellij.platform") version "2.1.0"】声明
}

//https://docs.gradle.org/current/userguide/dependency_configurations.html
dependencies {
    //IntelliJ Platform Gradle Plugin enhances the dependencies {} configuration block by applying a nested dependencies.intellijPlatform {} extension.
    intellijPlatform {
        //Target IDE Platform and Version，only one IntelliJ Platform dependency can be added to the project at a time.
        // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#setting-up-intellij-platform
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        //intellijIdeaCommunity("2024.2")//也可以使用这个指定

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        instrumentationTools()//if not,execution failed for task ':instrumentCode'.

        zipSigner()//插件签名使用 Marketplace ZIP Signer executable
    }
    //implementation: Dependencies required for both compilation and runtime.
    //implementation(kotlin("stdlib")) //[intellijIdeaCommunity]依赖中已含有
}

//The IntelliJ Platform Gradle Plugin introduces a top-level intellijPlatform extension
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
        privateKeyFile = providers.environmentVariable("PRIVATE_KEY_FILE").map { file(it) }
        certificateChainFile = providers.environmentVariable("CERTIFICATE_CHAIN_FILE").map { file(it) }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    //Set the JVM language level used to build the project.
    jvmToolchain(17)
}

// 固定 Java 编译编码，避免依赖 Gradle 守护进程的默认编码（IDEA 下 UTF-8、命令行下 GBK）导致中文常量乱码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

//use register() to register a task
tasks.register("hello") {// 任务名为"hello"
    group = "custom"
    description = "A lovely greeting task."
    doLast {
        println("Hello world!")
    }
}
//use named() to configure an existing task registered
tasks.named<Wrapper>("wrapper") {//对[wrapper]任务进行配置，用于改变使用的gradle版本
    gradleVersion = providers.gradleProperty("gradleVersion").get()
}
//也可以使用如下方式配置已注册的存在的任务，其实就是Kotlin DSL做了包装
//tasks {
//    wrapper {
//        gradleVersion = providers.gradleProperty("gradleVersion").get()
//    }
//}