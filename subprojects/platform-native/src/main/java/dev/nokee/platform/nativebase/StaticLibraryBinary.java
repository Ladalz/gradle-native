package dev.nokee.platform.nativebase;

import dev.nokee.platform.nativebase.tasks.CreateStaticLibrary;
import org.gradle.api.tasks.TaskProvider;

public interface StaticLibraryBinary extends NativeBinary {
	/**
	 * Returns a provider for the task that creates the static archive from the object files.
	 *
	 * @return a provider of {@link CreateStaticLibrary} task, never null.
	 */
	TaskProvider<? extends CreateStaticLibrary> getCreateTask();
}
