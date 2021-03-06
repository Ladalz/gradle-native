plugins {
	id("dev.gradleplugins.java-gradle-plugin") version("0.0.82")
	id("dev.gradleplugins.gradle-plugin-unit-test") version("0.0.82")
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
	mavenCentral()
}

test {
	dependencies {
		implementation(spockFramework())
		implementation(groovy())
	}
}

functionalTest {
	dependencies {
		implementation(spockFramework())
		implementation(groovy())
		implementation(gradleTestKit())
	}
}
