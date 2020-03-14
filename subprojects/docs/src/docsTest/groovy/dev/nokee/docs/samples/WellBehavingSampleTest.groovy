package dev.nokee.docs.samples

import dev.gradleplugins.spock.lang.CleanupTestDirectory
import dev.gradleplugins.spock.lang.TestNameTestDirectoryProvider
import dev.gradleplugins.test.fixtures.file.TestFile
import dev.gradleplugins.test.fixtures.gradle.GradleExecuterFactory
import dev.gradleplugins.test.fixtures.gradle.GradleScriptDsl
import dev.gradleplugins.test.fixtures.gradle.executer.GradleExecuter
import dev.gradleplugins.test.fixtures.gradle.executer.LogContent
import dev.gradleplugins.test.fixtures.gradle.executer.OutputScrapingExecutionResult
import dev.gradleplugins.test.fixtures.logging.ConsoleOutput
import dev.nokee.docs.fixtures.Command
import dev.nokee.docs.fixtures.SampleContentFixture
import groovy.transform.ToString
import org.apache.commons.lang3.StringUtils
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@CleanupTestDirectory
abstract class WellBehavingSampleTest extends Specification {
	@Rule
	final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

	protected GradleExecuter configureLocalPluginResolution(GradleExecuter executer) {
		def initScriptFile = temporaryFolder.file('repo.init.gradle')
		initScriptFile << """
			settingsEvaluated { settings ->
				settings.pluginManagement {
					repositories {
						maven {
							name = 'docs'
							url = '${System.getProperty('dev.nokee.docsRepository')}'
						}
					}
				}
			}
		"""
		return executer.usingInitScript(initScriptFile)
	}

	// TODO TEST: Ensure settings.gradle[.kts] contains sample name as rootProject.name
	// TODO TEST: Make sure Gradle build result doesn't contain any timing values

	protected abstract String getSampleName();

	// TODO: Migrate to TestFile
	protected String unzipTo(TestFile zipFile, File workingDirectory) {
		zipFile.assertIsFile()
		workingDirectory.mkdirs()
		return assertSuccessfulExecution(['unzip', zipFile.getCanonicalPath(), '-d', workingDirectory.getCanonicalPath()])
	}

	private String assertSuccessfulExecution(List<String> commandLine, File workingDirectory = null) {
		def process = commandLine.execute(null, workingDirectory)
		def outStream = new ByteArrayOutputStream()
		def stdoutThread = Thread.start { process.in.eachByte { print(new String(it)); outStream.write(it) } }
		def stderrThread = Thread.start { process.err.eachByte { print(new String(it)) } }
		assert process.waitFor(30, TimeUnit.SECONDS)
		assert process.exitValue() == 0
		stdoutThread.join(5000)
		stderrThread.join(5000)
		return outStream.toString()
	}

	@Unroll
	def "can run './gradlew #taskName' successfully"(taskName, dsl) {
		def fixture = new SampleContentFixture(sampleName)
		unzipTo(fixture.getDslSample(dsl), temporaryFolder.testDirectory)

		GradleExecuter executer = configureLocalPluginResolution(new GradleExecuterFactory().wrapper(TestFile.of(temporaryFolder.testDirectory)))
		expect:
		executer.withTasks(taskName).run()

		where:
		taskName << ['help', 'tasks']
		dsl << [GradleScriptDsl.GROOVY_DSL, GradleScriptDsl.KOTLIN_DSL]
	}

	@Unroll
	def "can execute commands successfully"(dsl) {
		def fixture = new SampleContentFixture(sampleName)
		unzipTo(fixture.getDslSample(dsl), temporaryFolder.testDirectory)

		expect:
		def c = wrap(fixture.getCommands())
		c.each { it.execute(TestFile.of(temporaryFolder.testDirectory)) }

		where:
		dsl << [GradleScriptDsl.GROOVY_DSL, GradleScriptDsl.KOTLIN_DSL]
	}

	private List<? super Comm> wrap(List<Command> commands) {
		commands.collect { command ->
			if (command.executable == './gradlew') {
				return new GradleWrapperCommand(command)
			} else if (command.executable == 'ls') {
				return new ListDirectoryCommand(command)
			} else if (command.executable == 'mv') {
				return new MoveFilesCommand(command)
			} else if (command.executable == 'unzip') {
				return new UnzipCommand(command)
			}
			return new GenericCommand(command)
		}
	}

	@ToString
	private static abstract class Comm {
		protected final Command command

		Comm(Command command) {
			this.command = command
		}

		abstract void execute(TestFile testDirectory);
	}

	private class GradleWrapperCommand extends Comm {
		GradleWrapperCommand(Command command) {
			super(command)
		}

		@Override
		void execute(TestFile testDirectory) {
			GradleExecuter executer = configureLocalPluginResolution(new GradleExecuterFactory().wrapper(TestFile.of(testDirectory)).withConsole(ConsoleOutput.Rich))

			def result = executer.withArguments(command.args).run()
			def expectedResult = OutputScrapingExecutionResult.from(command.expectedOutput.get(), '')

			assert OutputScrapingExecutionResult.normalize(LogContent.of(result.getPlainTextOutput())).replace(' in 0s', '').startsWith(expectedResult.getOutput())
		}
	}

	private class ListDirectoryCommand extends Comm {

		ListDirectoryCommand(Command command) {
			super(command)
		}

		@Override
		void execute(TestFile testDirectory) {
			def process = (['/bin/bash', '-c'] + [([command.executable] + command.args).join(' ')]).execute(null, testDirectory)
			assert process.waitFor() == 0
			assert process.in.text.startsWith(command.expectedOutput.get())
		}
	}

	private class MoveFilesCommand extends Comm {

		MoveFilesCommand(Command command) {
			super(command)
		}

		@Override
		void execute(TestFile testDirectory) {
			def process = (['/bin/bash', '-c'] + ([command.executable] + command.args).join(' ')).execute(null, testDirectory)
			assert process.waitFor() == 0
			assert process.in.text.trim().empty
		}
	}

	private class UnzipCommand extends Comm {
		UnzipCommand(Command command) {
			super(command)
		}

		@Override
		void execute(TestFile testDirectory) {
			TestFile inputFile = testDirectory.file(command.args[0])
			TestFile outputDirectory = testDirectory.file(command.args[command.args.findIndexOf {it == '-d'} + 1])

			String stdout = unzipTo(inputFile, outputDirectory)
			// unzip add extra newline but also have extra tailing spaces
			stdout = stdout.readLines().collect { StringUtils.stripEnd(it, ' ')}.join('\n')
			assert stdout.replace(testDirectory.absolutePath, '/Users/daniel') == command.expectedOutput.get()
		}
	}

	private class GenericCommand extends Comm {
		GenericCommand(Command command) {
			super(command)
		}

		@Override
		void execute(TestFile testDirectory) {
			def process = (['/bin/bash', '-c'] + ([command.executable] + command.args).join(' ')).execute(null, testDirectory)
			assert process.waitFor() == 0
			assert process.in.text.startsWith(command.getExpectedOutput().get())
		}
	}
}
