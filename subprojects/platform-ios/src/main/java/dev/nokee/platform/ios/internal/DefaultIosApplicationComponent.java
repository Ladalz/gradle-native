package dev.nokee.platform.ios.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.nokee.core.exec.CommandLineTool;
import dev.nokee.core.exec.internal.PathAwareCommandLineTool;
import dev.nokee.core.exec.internal.VersionedCommandLineTool;
import dev.nokee.language.objectivec.tasks.ObjectiveCCompile;
import dev.nokee.platform.base.BinaryAwareComponent;
import dev.nokee.platform.base.DependencyAwareComponent;
import dev.nokee.platform.base.internal.BuildVariant;
import dev.nokee.platform.base.internal.GroupId;
import dev.nokee.platform.base.internal.NamingScheme;
import dev.nokee.platform.ios.tasks.internal.*;
import dev.nokee.platform.nativebase.ExecutableBinary;
import dev.nokee.platform.nativebase.NativeComponentDependencies;
import dev.nokee.platform.nativebase.internal.*;
import dev.nokee.platform.nativebase.tasks.LinkExecutable;
import dev.nokee.runtime.nativebase.internal.DefaultMachineArchitecture;
import dev.nokee.runtime.nativebase.internal.DefaultOperatingSystemFamily;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.nativeplatform.toolchain.Swiftc;
import org.gradle.util.GUtil;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static dev.nokee.platform.ios.internal.plugins.IosApplicationRules.getSdkPath;

public abstract class DefaultIosApplicationComponent extends BaseNativeComponent<DefaultIosApplicationVariant> implements DependencyAwareComponent<NativeComponentDependencies>, BinaryAwareComponent {
	private final DefaultNativeComponentDependencies dependencies;
	private final GroupId groupId;

	@Inject
	public DefaultIosApplicationComponent(NamingScheme names, GroupId groupId) {
		super(names, DefaultIosApplicationVariant.class);
		this.groupId = groupId;
		this.dependencies = getObjects().newInstance(DefaultNativeComponentDependencies.class, getNames());
		getDimensions().convention(ImmutableSet.of(DefaultOperatingSystemFamily.DIMENSION_TYPE, DefaultMachineArchitecture.DIMENSION_TYPE, DefaultBinaryLinkage.DIMENSION_TYPE));
	}

	@Override
	public DefaultNativeComponentDependencies getDependencies() {
		return dependencies;
	}

	@Override
	public void dependencies(Action<? super NativeComponentDependencies> action) {
		action.execute(dependencies);
	}

	public Provider<? extends BaseNativeVariant> getDevelopmentVariant() {
		// By default, we should filter for the variant targeting the simulator
		// Here we assume only one variant that target the simulator ;-)
		return getProviders().provider(() -> {
			List<BaseNativeVariant> variants = getVariants().get().stream().map(it -> {
				Preconditions.checkArgument(it instanceof BaseNativeVariant);
				return (BaseNativeVariant) it;
			}).collect(Collectors.toList());

			if (variants.isEmpty()) {
				return null;
			}
			return one(variants);
		});
	}

	@Override
	protected DefaultIosApplicationVariant createVariant(String name, BuildVariant buildVariant) {
		NamingScheme names = getNames().forBuildVariant(buildVariant, getBuildVariants().get());
		DefaultNativeComponentDependencies variantDependencies = getDependencies();
		if (getBuildVariants().get().size() > 1) {
			variantDependencies = getObjects().newInstance(DefaultNativeComponentDependencies.class, names);
			variantDependencies.extendsFrom(getDependencies());
		}

		DefaultIosApplicationVariant result = getObjects().newInstance(DefaultIosApplicationVariant.class, name, names, buildVariant, variantDependencies);
		return result;
	}

	@Inject
	protected abstract DependencyHandler getDependencyHandler();

	@Override
	protected void onEachVariant(BuildVariant buildVariant, NamedDomainObjectProvider<? extends BaseNativeVariant> variant) {
		variant.configure(application -> {
			application.getBinaries().configureEach(ExecutableBinary.class, binary -> {
				binary.getCompileTasks().configureEach(task -> {
					task.getCompilerArgs().addAll(getProviders().provider(() -> ImmutableList.of("-target", "x86_64-apple-ios13.2-simulator", "-F", getSdkPath() + "/System/Library/Frameworks")));
					task.getCompilerArgs().addAll(task.getToolChain().map(toolChain -> {
						if (toolChain instanceof Swiftc) {
							return ImmutableList.of("-sdk", getSdkPath());
						}
						return ImmutableList.of("-isysroot", getSdkPath());
					}));
					if (task instanceof ObjectiveCCompile) {
						task.getCompilerArgs().addAll("-fobjc-arc");
					}
				});
				binary.getLinkTask().configure(task -> {
					task.getLinkerArgs().addAll(getProviders().provider(() -> ImmutableList.of("-target", "x86_64-apple-ios13.2-simulator")));
					task.getLinkerArgs().addAll(task.getToolChain().map(toolChain -> {
						if (toolChain instanceof Swiftc) {
							return ImmutableList.of("-sdk", getSdkPath());
						}
						return ImmutableList.of("-isysroot", getSdkPath());
					}));
					task.getLinkerArgs().addAll("-Xlinker", "-rpath", "-Xlinker", "@executable_path/Frameworks",
						"-Xlinker", "-export_dynamic",
						"-Xlinker", "-no_deduplicate",
						"-Xlinker", "-objc_abi_version", "-Xlinker", "2",
//					"-Xlinker", "-sectcreate", "-Xlinker", "__TEXT", "-Xlinker", "__entitlements", "-Xlinker", createEntitlementTask.get().outputFile.get().asFile.absolutePath
						"-lobjc", "-framework", "UIKit", "-framework", "Foundation");
					// TODO: -lobjc should probably only be present for binary compiling/linking objc binaries
				});
			});

			// Create iOS application specific tasks
			Configuration interfaceBuilderToolConfiguration = getConfigurations().create("interfaceBuilderTool");
			interfaceBuilderToolConfiguration.getDependencies().add(getDependencyHandler().create("dev.nokee.tool:ibtool:latest.release"));
			Provider<CommandLineTool> interfaceBuilderTool = getProviders().provider(() -> new DescriptorCommandLineTool(interfaceBuilderToolConfiguration.getSingleFile()));

			Provider<CommandLineTool> assetCompilerTool = getProviders().provider(() -> new VersionedCommandLineTool(new File("/usr/bin/actool"), VersionNumber.parse("11.3.1")));
			Provider<CommandLineTool> codeSignatureTool = getProviders().provider(() -> new PathAwareCommandLineTool(new File("/usr/bin/codesign")));

			String moduleName = getNames().getBaseName().getAsCamelCase();
			Provider<String> identifier = getProviders().provider(() -> groupId.get().map(it -> it + "." + moduleName).orElse(moduleName));

			TaskProvider<StoryboardCompileTask> compileStoryboardTask = getTasks().register("compileStoryboard", StoryboardCompileTask.class, task -> {
				task.getDestinationDirectory().set(getLayout().getBuildDirectory().dir("ios/storyboards/compiled/main"));
				task.getModule().set(moduleName);
				task.getSources().from(getObjects().fileTree().setDir("src/main/resources").matching(it -> it.include("*.lproj/*.storyboard")));
				task.getInterfaceBuilderTool().set(interfaceBuilderTool);
				task.getInterfaceBuilderTool().finalizeValueOnRead();
			});

			TaskProvider<StoryboardLinkTask> linkStoryboardTask = getTasks().register("linkStoryboard", StoryboardLinkTask.class, task -> {
				task.getDestinationDirectory().set(getLayout().getBuildDirectory().dir("ios/storyboards/linked/main"));
				task.getModule().set(moduleName);
				task.getSources().from(compileStoryboardTask.flatMap(StoryboardCompileTask::getDestinationDirectory));
				task.getInterfaceBuilderTool().set(interfaceBuilderTool);
				task.getInterfaceBuilderTool().finalizeValueOnRead();
			});

			TaskProvider<AssetCatalogCompileTask> assetCatalogCompileTaskTask = getTasks().register("compileAssetCatalog", AssetCatalogCompileTask.class, task -> {
				task.getSource().set(getLayout().getProjectDirectory().file("src/main/resources/Assets.xcassets"));
				task.getIdentifier().set(identifier);
				task.getDestinationDirectory().set(getLayout().getBuildDirectory().dir("ios/assets/main"));
				task.getAssetCompilerTool().set(assetCompilerTool);
			});

			TaskProvider<ProcessPropertyListTask> processPropertyListTask = getTasks().register("processPropertyList", ProcessPropertyListTask.class, task -> {
				task.getIdentifier().set(identifier);
				task.getModule().set(moduleName);
				task.getSources().from(getProviders().provider(() -> {
					// TODO: I'm not sure we should jump through some hoops for a missing Info.plist.
					//  I'm under the impression that a missing Info.plist file is an error and should be failing in some way.
					// TODO: Regardless of what we do above, the "skip when empty" should be handled by the task itself
					File plistFile = getLayout().getProjectDirectory().file("src/main/resources/Info.plist").getAsFile();
					if (plistFile.exists()) {
						return ImmutableList.of(plistFile);
					}
					return ImmutableList.of();
				}));
				task.getOutputFile().set(getLayout().getBuildDirectory().file("ios/Info.plist"));
			});

			TaskProvider<CreateIosApplicationBundleTask> createApplicationBundleTask = getTasks().register("createApplicationBundle", CreateIosApplicationBundleTask.class, task -> {
				List<? extends Provider<RegularFile>> binaries = application.getBinaries().withType(ExecutableBinaryInternal.class).map(it -> it.getLinkTask().flatMap(LinkExecutable::getLinkedFile)).get();

				task.getExecutable().set(binaries.iterator().next()); // TODO: Fix this approximation
				task.getSwiftSupportRequired().convention(false);
				task.getApplicationBundle().set(getLayout().getBuildDirectory().file("ios/products/main/" + moduleName + "-unsigned.app"));
				task.getSources().from(linkStoryboardTask.flatMap(StoryboardLinkTask::getDestinationDirectory));
				// Linked file is configured in IosApplicationRules
				task.getSources().from(binaries);
				task.getSources().from(assetCatalogCompileTaskTask.flatMap(AssetCatalogCompileTask::getDestinationDirectory));
				task.getSources().from(processPropertyListTask.flatMap(ProcessPropertyListTask::getOutputFile));
			});
			application.getBinaryCollection().add(getObjects().newInstance(IosApplicationBundleInternal.class));

			TaskProvider<SignIosApplicationBundleTask> signApplicationBundleTask = getTasks().register("signApplicationBundle", SignIosApplicationBundleTask.class, task -> {
				task.getUnsignedApplicationBundle().set(createApplicationBundleTask.flatMap(CreateIosApplicationBundleTask::getApplicationBundle));
				task.getSignedApplicationBundle().set(getLayout().getBuildDirectory().file("ios/products/main/" + moduleName + ".app"));
				task.getCodeSignatureTool().set(codeSignatureTool);
			});

			application.getBinaryCollection().add(getObjects().newInstance(SignedIosApplicationBundleInternal.class));
		});

		TaskProvider<Task> bundle = getTasks().register("bundle", task -> {
			task.dependsOn(variant.map(it -> it.getBinaries().withType(SignedIosApplicationBundleInternal.class).get()));
		});
	}

	@Override
	public void finalizeExtension(Project project) {
		getVariants().configureEach(variant -> {
			variant.getBinaries().configureEach(BaseNativeBinary.class, binary -> {
				binary.getBaseName().convention(GUtil.toCamelCase(project.getName()));
			});
		});
		super.finalizeExtension(project);
	}
}