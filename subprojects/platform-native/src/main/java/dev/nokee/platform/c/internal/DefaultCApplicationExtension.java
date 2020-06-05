package dev.nokee.platform.c.internal;

import dev.nokee.language.c.internal.CSourceSet;
import dev.nokee.platform.base.DependencyAwareComponent;
import dev.nokee.platform.base.internal.NamingScheme;
import dev.nokee.platform.c.CApplicationExtension;
import dev.nokee.platform.nativebase.NativeComponentDependencies;
import dev.nokee.platform.nativebase.internal.BaseNativeExtension;
import dev.nokee.platform.nativebase.internal.DefaultNativeApplicationComponent;
import lombok.experimental.Delegate;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;

import javax.inject.Inject;

public abstract class DefaultCApplicationExtension extends BaseNativeExtension<DefaultNativeApplicationComponent> implements CApplicationExtension {
	@Inject
	public DefaultCApplicationExtension(NamingScheme names) {
		super(names, DefaultNativeApplicationComponent.class);
		getComponent().getSourceCollection().add(getObjects().newInstance(CSourceSet.class).srcDir("src/main/c"));
	}

	@Override
	public NativeComponentDependencies getDependencies() {
		return getComponent().getDependencies();
	}

	@Override
	public void dependencies(Action<? super NativeComponentDependencies> action) {
		getComponent().dependencies(action);
	}

	public void finalizeExtension(Project project) {
		getComponent().finalizeExtension(project);
	}
}
