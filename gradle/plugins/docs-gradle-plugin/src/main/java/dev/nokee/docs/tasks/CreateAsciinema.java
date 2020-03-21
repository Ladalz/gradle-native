package dev.nokee.docs.tasks;

import dev.nokee.docs.PluginManagementBlock;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import org.apache.commons.io.FileUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.jruby.ast.impl.ListImpl;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static org.asciidoctor.OptionsBuilder.options;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;

public abstract class CreateAsciinema extends ProcessorTask {
	private final List<Sample> samples = new ArrayList<>();

	public void sample(Action<? super Sample> action) {
		Sample result = getObjectFactory().newInstance(Sample.class);
		action.execute(result);
		samples.add(result);
	}

	@TaskAction
	private void doCreate() {
		samples.forEach(sample -> {
			getWorkerExecutor().classLoaderIsolation(it -> it.getClasspath().from(getClasspath())).submit(CreateAsciinemaAction.class, it -> {
				it.getContentFile().set(sample.getContentFile());
				it.getOutputFile().set(getOutputDirectory().file(getRelativePath().get() + "/" + sample.getPermalink().get() + "/all-commands.cast"));
				it.getLogFile().set(new File(getTemporaryDir(), "logs/" + getRelativePath().get() + "/" + sample.getPermalink().get() + "/all-commands.txt"));



				try {
					File workingDirectory = Files.createTempDirectory("").toFile();
					it.getSource().set(workingDirectory);
					getFileOperations().sync(spec -> {
						spec.from(getProject().zipTree(sample.getSource().get().getAsFile()));
						spec.into(workingDirectory);
					});

					File gradleUserHomeDirectory = Files.createTempDirectory("").toFile();
					File initScript = new File(gradleUserHomeDirectory, "init.d/init.gradle");
					initScript.getParentFile().mkdirs();
					FileUtils.write(initScript, PluginManagementBlock.asGroovyDsl().withVersion(getVersion().get()).withRepository(getLocalRepository().get().getAsFile().getAbsolutePath()).configureFromInitScript(), Charset.defaultCharset());
					Files.createSymbolicLink(new File(gradleUserHomeDirectory, "wrapper").toPath(), new File(System.getProperty("user.home") + "/.gradle/wrapper").toPath());
					it.getGradleUserHomeDirectory().set(gradleUserHomeDirectory);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		});
	}

	public interface CreateAsciinemaParameters extends WorkParameters {
		RegularFileProperty getContentFile();
		RegularFileProperty getOutputFile();
		DirectoryProperty getSource();

		RegularFileProperty getLogFile();

		DirectoryProperty getGradleUserHomeDirectory();
	}

	public static abstract class CreateAsciinemaAction implements WorkAction<CreateAsciinemaParameters> {
		private File outputFile;

		@Override
		public void execute() {
			Asciidoctor asciidoctor = Asciidoctor.Factory.create();
			Document document = asciidoctor.loadFile(getParameters().getContentFile().get().getAsFile(), options().asMap());
			List<Command> commands = CommandDiscovery.extractAsciidocCommands(document);

			outputFile = getParameters().getOutputFile().get().getAsFile();
			outputFile.getParentFile().mkdirs();

			primeGradle();
			initAsciicastFile();
			commands.forEach(command -> {
				recordToAsciicastFile(command);
			});
		}

		private void primeGradle() {
			getExecOperations().exec(spec -> {
//				spec.commandLine("./gradlew", "help");
				spec.commandLine("gradle", "help");
				spec.setWorkingDir(getParameters().getSource().get().getAsFile());
				try {
					File logFile = getParameters().getLogFile().get().getAsFile();
					logFile.getParentFile().mkdirs();
					OutputStream logStream = new FileOutputStream(logFile, true);
					spec.setErrorOutput(logStream);
					spec.setStandardOutput(logStream);
					spec.environment("GRADLE_USER_HOME", getParameters().getGradleUserHomeDirectory().get().getAsFile().getAbsolutePath());
				} catch (FileNotFoundException e) {
					throw new UncheckedIOException(e);
				}
			});
		}

		private void exec(String... commandLine) {
			getExecOperations().exec(spec -> {
				spec.commandLine(asList(commandLine));
				spec.setWorkingDir(getParameters().getSource().get().getAsFile());
				try {
					File logFile = getParameters().getLogFile().get().getAsFile();
					logFile.getParentFile().mkdirs();
					OutputStream logStream = new FileOutputStream(logFile, true);
					spec.setErrorOutput(logStream);
					spec.setStandardOutput(logStream);
					spec.environment("GRADLE_USER_HOME", getParameters().getGradleUserHomeDirectory().get().getAsFile().getAbsolutePath());
				} catch (FileNotFoundException e) {
					throw new UncheckedIOException(e);
				}
			});
		}

		private void initAsciicastFile() {
			exec("asciinema", "rec", "-c printf '$ '", "--overwrite", outputFile.getAbsolutePath());
			exec("asciinema", "rec", "-c sleep 1", "--append", outputFile.getAbsolutePath());
		}

		private void recordToAsciicastFile(Command command) {
			exec("asciinema", "rec", "-c echo " + command.commandLine.get(), "--append", outputFile.getAbsolutePath());

			if (command.commandLine.executable.equals("./gradlew")) {
				exec("asciinema", "rec", "-c " + command.commandLine.withExecutable("gradle").get(), "--append", outputFile.getAbsolutePath());
			} else {
				exec("asciinema", "rec", "-c " + command.commandLine.get(), "--append", outputFile.getAbsolutePath());
			}
			exec("asciinema", "rec", "-c printf '$ '", "--append", outputFile.getAbsolutePath());
			exec("asciinema", "rec", "-c sleep 1", "--append", outputFile.getAbsolutePath());
		}

		@Inject
		protected abstract ExecOperations getExecOperations();

		@Inject
		protected abstract FileSystemOperations getFileOperations();
	}

	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	@Input
	public abstract Property<String> getRelativePath();

	@Input
	public abstract Property<String> getVersion();

	public abstract DirectoryProperty getLocalRepository();

	@Inject
	protected abstract FileSystemOperations getFileOperations();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@Inject
	protected abstract ObjectFactory getObjectFactory();


	public interface Sample {
		@InputFile
		RegularFileProperty getContentFile();

		@Input
		Property<String> getPermalink();

		@InputFile
		RegularFileProperty getSource();

		@Input
		Property<String> getVersion();

		@Input
		Property<String> getArchiveBaseName();
	}

	@Value
	public static class CommandLine {
		@With @NonNull String executable;
		@NonNull List<String> arguments;

		private CommandLine(String executable, List<String> arguments) {
			this.executable = executable;
			this.arguments = arguments;
		}

		public String get() {
			return executable + " " + String.join(" ", getArguments());
		}

		public String getExecutable() {
			return executable;
		}

		public List<String> getArguments() {
			return arguments;
		}

		public static CommandLine of(String commandLine) {
			String[] commandLineWords = commandLine.split("\\s+");
			String executable = commandLineWords[0];

			List<String> arguments = Collections.emptyList();
			if (commandLineWords.length > 1) {
				arguments = Arrays.asList(Arrays.copyOfRange(commandLineWords, 1, commandLineWords.length));
			}

			return new CommandLine(executable, arguments);
		}
	}

	public static class Command {
		private final CommandLine commandLine;
		private final Optional<String> executionSubdirectory;
		private final List<String> flags;
		private final Optional<String> expectedOutput;
		private final boolean expectFailure;
		private final boolean allowAdditionalOutput;
		private final boolean allowDisorderedOutput;
		private final List<String> userInputs;

		public Command(CommandLine commandLine, Optional<String> executionDirectory, List<String> flags, Optional<String> expectedOutput, boolean expectFailure, boolean allowAdditionalOutput, boolean allowDisorderedOutput, List<String> userInputs) {
			this.commandLine = commandLine;
			this.executionSubdirectory = executionDirectory;
			this.flags = flags;
			this.expectedOutput = expectedOutput;
			this.expectFailure = expectFailure;
			this.allowAdditionalOutput = allowAdditionalOutput;
			this.allowDisorderedOutput = allowDisorderedOutput;
			this.userInputs = userInputs;
		}

		public String getExecutable() {
			return commandLine.getExecutable();
		}

		public Optional<String> getExecutionSubdirectory() {
			return executionSubdirectory;
		}

		public List<String> getArgs() {
			return commandLine.getArguments();
		}

		public List<String> getFlags() {
			return flags;
		}

		public Optional<String> getExpectedOutput() {
			return expectedOutput;
		}

		/**
		 * @return true if executing the scenario build is expected to fail.
		 */
		public boolean isExpectFailure() {
			return expectFailure;
		}

		/**
		 * @return true if output lines other than those provided are allowed.
		 */
		public boolean isAllowAdditionalOutput() {
			return allowAdditionalOutput;
		}

		/**
		 * @return true if actual output lines can differ in order from expected.
		 */
		public boolean isAllowDisorderedOutput() {
			return allowDisorderedOutput;
		}

		/**
		 * @return a list of user inputs to provide to the command
		 */
		public List<String> getUserInputs() {
			return userInputs;
		}
	}

	public static class CommandDiscovery {
		private static final String COMMAND_PREFIX = "$ ";

		public static List<Command> extractAsciidocCommands(StructuralNode testableSampleBlock) {
			List<Command> commands = new ArrayList<>();
			Queue<StructuralNode> queue = new ArrayDeque<>();
			queue.add(testableSampleBlock);
			while (!queue.isEmpty()) {
				StructuralNode node = queue.poll();
				if (node instanceof ListImpl) {
					queue.addAll(((ListImpl) node).getItems());
				} else {
					for (StructuralNode child : node.getBlocks()) {
						if (child.isBlock() && child.hasRole("terminal")) {
							parseEmbeddedCommand((Block) child, commands);
						} else {
							queue.offer(child);
						}
					}
				}
			}

			return commands;
		}

		private static void parseEmbeddedCommand(Block block, List<Command> commands) {
			Map<String, Object> attributes = block.getAttributes();
			String[] lines = block.getSource().split("\r?\n");
			int pos = 0;

			do {
				pos = parseOneCommand(lines, pos, attributes, commands);
			} while (pos < lines.length);
		}

		private static int parseOneCommand(String[] lines, int pos, Map<String, Object> attributes, List<Command> commands) {
			String commandLineString = lines[pos];
			if (!commandLineString.startsWith(COMMAND_PREFIX)) {
				throw new RuntimeException("Inline sample command " + commandLineString);
			}

			CommandLine commandLine = CommandLine.of(commandLineString.substring(COMMAND_PREFIX.length()));

			StringBuilder expectedOutput = new StringBuilder();
			int nextCommand = pos + 1;
			while (nextCommand < lines.length && !lines[nextCommand].startsWith(COMMAND_PREFIX)) {
				if (nextCommand > pos + 1) {
					expectedOutput.append("\n");
				}
				expectedOutput.append(lines[nextCommand]);
				nextCommand++;
			}

			Command command = new Command(commandLine,
				Optional.<String>empty(),
				Collections.<String>emptyList(),
				Optional.of(expectedOutput.toString()),
				attributes.containsKey("expect-failure"),
				attributes.containsKey("allow-additional-output"),
				attributes.containsKey("allow-disordered-output"),
				Collections.emptyList());
			commands.add(command);
			return nextCommand;
		}
	}
}
