plugins {
	id("java-library")
}

description = "The JNI classes, also known as the JVM bindings."

dependencies {
	implementation(project(":java-loader"))
}
