buildscript {
	repositories {
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/")
	}

	dependencies {
		classpath("net.fabricmc:fabric-loom:1.8.6")
	}
}

apply(plugin = "java")

pluginManager.withPlugin("fabric-loom") {
	extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom") {
		extensions.getByName<SourceSetContainer>("sourceSets").forEach {
			it.resources.files
				.find { file -> file.name.endsWith(".accesswidener") }
				?.let(accessWidenerPath::set)
		}

		@Suppress("UnstableApiUsage")
		mixin {
			defaultRefmapName.set("${property("mod_id")}.refmap.json")
		}

		runs {
			getByName("client") {
				configName = "Minecraft Client"
				runDir = "run/client"
				client()
			}

			getByName("server") {
				configName = "Minecraft Server"
				runDir = "run/server"
				server()
			}
		}
	}

	tasks {
		named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
			archiveAppendix.set("fabric")
			archiveVersion.set("${project.version}+mc${project.property("minecraft_version")}")
		}
	}

	afterEvaluate {
		extensions.getByName<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom").runs.configureEach {
			// net.fabricmc.loader.impl.util.SystemProperties
			// org.spongepowered.asm.mixin.MixinEnvironment
			vmArgs(
				"-XX:+IgnoreUnrecognizedVMOptions",
				"-XX:+AllowEnhancedClassRedefinition",
				"-XX:HotswapAgent=fatjar",
				"-Dfabric.development=true",
				"-Dmixin.debug.export=true",
				"-Dmixin.debug.verify=true",
//				"-Dmixin.debug.strict=true",
				"-Dmixin.debug.countInjections=true",
				"-Dmixin.checks.interfaces=true",
			)

			java.nio.file.Path.of(System.getProperty("java.home"), "lib/hotswap/hotswap-agent.jar")
				.takeIf { java.nio.file.Files.exists(it) }
				.let { vmArg("-Dfabric.systemLibraries=$it") }

			configurations["compileClasspath"]
				.files { it.group == "net.fabricmc" && it.name == "sponge-mixin" }
				.firstOrNull()
				.let { vmArg("-javaagent:$it") }
		}
	}
}

val targetJavaVersion = JavaVersion.toVersion(property("targetCompatibility")!!)
extensions.configure<JavaPluginExtension>("java") {
	targetCompatibility = targetJavaVersion
	sourceCompatibility = targetJavaVersion
	if (JavaVersion.current() < targetJavaVersion)
		toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion.majorVersion.toInt()))
}

tasks {
	named<JavaCompile>("compileJava") {
		if (targetJavaVersion.majorVersion.toInt() >= 10 || JavaVersion.current().isJava10Compatible)
			options.release.set(targetJavaVersion.majorVersion.toInt())
		options.encoding = "UTF-8"
	}

	named<ProcessResources>("processResources") {
		inputs.properties(
			"id" to project.property("mod_id"),
			"name" to project.property("mod_name"),
			"version" to project.version,
			"java_version" to targetJavaVersion.majorVersion,
			"minecraft_version" to project.property("minecraft_version"),
			"loader_version" to project.property("loader_version"),
		)
		filesMatching("fabric.mod.json") {
			expand(inputs.properties)
		}
	}

	named<Jar>("jar") {
		from("LICENSE.txt")
		archiveAppendix.set("fabric")
		archiveVersion.set("${project.version}+mc${project.property("minecraft_version")}")
	}
}
