plugins {
	id("dev.gradleplugins.java-gradle-plugin") version("0.0.82")
	id("dev.gradleplugins.gradle-plugin-functional-test") version("0.0.82")
}

gradlePlugin {
	plugins {
		create("helloWorld") {
			id = "com.example.hello"
			implementationClass = "com.example.BasicPlugin"
		}
	}
}

repositories {
	jcenter()
}

functionalTest {
	testingStrategies.set(listOf(strategies.coverageForMinimumVersion, strategies.coverageForLatestGlobalAvailableVersion, strategies.coverageForLatestNightlyVersion))
	dependencies {
		implementation(spockFramework())
		implementation(groovy())
		implementation(gradleFixtures())	// <1>
		implementation(gradleTestKit())
	}
}
