package dev.nokee.platform.nativebase.internal;

import com.google.common.collect.ImmutableSet;
import dev.nokee.platform.base.BinaryAwareComponent;
import dev.nokee.platform.base.DependencyAwareComponent;
import dev.nokee.platform.base.internal.BuildVariant;
import dev.nokee.platform.base.internal.NamingScheme;
import dev.nokee.platform.nativebase.NativeLibraryDependencies;
import dev.nokee.runtime.nativebase.internal.DefaultMachineArchitecture;
import dev.nokee.runtime.nativebase.internal.DefaultOperatingSystemFamily;
import org.gradle.api.Action;

import javax.inject.Inject;

public abstract class DefaultNativeLibraryComponent extends BaseNativeComponent<DefaultNativeLibraryVariant> implements DependencyAwareComponent<NativeLibraryDependencies>, BinaryAwareComponent {
	private final DefaultNativeLibraryDependencies dependencies;

	@Inject
	public DefaultNativeLibraryComponent(NamingScheme names) {
		super(names, DefaultNativeLibraryVariant.class);
		this.dependencies = getObjects().newInstance(DefaultNativeLibraryDependencies.class, getNames());
		getDimensions().convention(ImmutableSet.of(DefaultOperatingSystemFamily.DIMENSION_TYPE, DefaultMachineArchitecture.DIMENSION_TYPE, DefaultBinaryLinkage.DIMENSION_TYPE));
	}

	@Override
	public DefaultNativeLibraryDependencies getDependencies() {
		return dependencies;
	}

	public void dependencies(Action<? super NativeLibraryDependencies> action) {
		action.execute(dependencies);
	}

	@Override
	protected DefaultNativeLibraryVariant createVariant(String name, BuildVariant buildVariant) {
		NamingScheme names = getNames().forBuildVariant(buildVariant, getBuildVariants().get());
		DefaultNativeLibraryDependencies variantDependencies = getDependencies();
		if (getBuildVariants().get().size() > 1) {
			variantDependencies = getObjects().newInstance(DefaultNativeLibraryDependencies.class, names);
			variantDependencies.extendsFrom(getDependencies());
		}

		DefaultNativeLibraryVariant result = getObjects().newInstance(DefaultNativeLibraryVariant.class, name, names, buildVariant, variantDependencies);
		return result;
	}
}
