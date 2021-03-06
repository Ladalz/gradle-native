plugins {
	id("java-library")
	id("groovy-base")
}

import dev.gradleplugins.GradleRuntimeCompatibility.*

java {
	targetCompatibility = minimumJavaVersionFor("4.9")	// <1>
	sourceCompatibility = minimumJavaVersionFor("4.9")	// <1>
}

repositories {
	gradlePluginDevelopment()							// <2>
	mavenCentral()										// <3>
}

dependencies {
	compileOnly(gradleApi("4.9"))						// <4>

	testImplementation(gradleApi("4.9"))				// <5>
	testImplementation(platform("org.spockframework:spock-bom:1.2-groovy-2.4"))
	testImplementation("org.spockframework:spock-core")
}
