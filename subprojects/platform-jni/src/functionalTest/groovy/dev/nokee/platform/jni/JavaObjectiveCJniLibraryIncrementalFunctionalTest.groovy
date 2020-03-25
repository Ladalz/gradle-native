package dev.nokee.platform.jni

import dev.gradleplugins.test.fixtures.sources.SourceElement
import dev.nokee.platform.jni.fixtures.JavaJniObjectiveCGreeterLib

class JavaObjectiveCJniLibraryIncrementalFunctionalTest extends AbstractJavaJniLibraryIncrementalFunctionalTest {
	@Override
	protected void makeSingleProject() {
		buildFile << '''
			plugins {
				id 'java'
				id 'dev.nokee.jni-library'
				id 'dev.nokee.objective-c-language'
			}

			// Internal details
			tasks.withType(LinkSharedLibrary) {
				linkerArgs.add('-lobjc')
			}
		'''
		settingsFile << "rootProject.name = 'jni-greeter'"
	}

	@Override
	protected SourceElement getComponentUnderTest() {
		return new JavaJniObjectiveCGreeterLib('jni-greeter')
	}
}
