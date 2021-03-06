plugins {
	id("dev.nokee.objective-cpp-library")
	id("dev.nokee.xcode-ide")
}

import dev.nokee.platform.nativebase.SharedLibraryBinary

library.variants.configureEach {
	binaries.configureEach(SharedLibraryBinary::class.java) {
		linkTask.configure {
			linkerArgs.add("-lobjc")
		}
	}
}
