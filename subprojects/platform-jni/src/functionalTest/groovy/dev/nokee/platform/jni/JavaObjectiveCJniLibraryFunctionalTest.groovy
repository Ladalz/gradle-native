package dev.nokee.platform.jni

import dev.nokee.language.objectivec.ObjectiveCTaskNames
import dev.nokee.platform.jni.fixtures.JavaJniObjectiveCGreeterLib

import static org.hamcrest.CoreMatchers.containsString

class JavaObjectiveCJniLibraryFunctionalTest extends AbstractJavaJniLibraryFunctionalTest implements ObjectiveCTaskNames {
	def "build fails when Objective-C compilation fails"() {
		given:
		makeSingleProject()

		and:
		file("src/main/objc/broken.m") << "broken!"

		expect:
		fails "assemble"
		failure.assertHasDescription("Execution failed for task '$developmentBinaryNativeCompileTask'.")
		failure.assertHasCause("A build operation failed.")
		failure.assertThatCause(containsString("Objective-C compiler failed while compiling broken.m"))
	}


//	def "can compile and link against a library"() {
// TODO: JNI Plugin should allow to work alone by embedding the runtime native dependencies into a JAR. It should also create an empty JAR when no language (not even Java). This Jar should be exposed via API outgoing dependencies.

	// TODO: Should be abstracted
	@Override
	protected String getDevelopmentBinaryNativeCompileTask() {
		return tasks.compile
	}

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
	JavaJniObjectiveCGreeterLib getComponentUnderTest() {
		return new JavaJniObjectiveCGreeterLib('jni-greeter')
	}
}
