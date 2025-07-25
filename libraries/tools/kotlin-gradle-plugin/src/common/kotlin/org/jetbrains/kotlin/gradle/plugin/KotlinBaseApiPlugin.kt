/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAgpCompilationFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTargetForJvm
import org.jetbrains.kotlin.gradle.plugin.sources.android.AndroidVariantType
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.configuration.KaptGenerateStubsConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KaptWithoutKotlincConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KotlinCompileConfig
import org.jetbrains.kotlin.gradle.utils.KotlinJvmCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.utils.injected
import javax.inject.Inject

/** Plugin that can be used by third-party plugins to create Kotlin-specific DSL and tasks (compilation and KAPT) for JVM platform. */
abstract class KotlinBaseApiPlugin : DefaultKotlinBasePlugin(), KotlinJvmFactory {

    private lateinit var myProject: Project
    private val taskCreator = KotlinTasksProvider()

    @get:Inject
    override val providerFactory: ProviderFactory
        get() = injected

    override fun apply(project: Project) {
        super.apply(project)
        myProject = project
        setupAttributeMatchingStrategy(project)
    }

    override fun addCompilerPluginDependency(dependency: Provider<Any>) {
        myProject.dependencies.addProvider(
            PLUGIN_CLASSPATH_CONFIGURATION_NAME,
            dependency
        )
    }

    override fun getCompilerPlugins(): FileCollection {
        return myProject.configurations.getByName(PLUGIN_CLASSPATH_CONFIGURATION_NAME)
    }

    override fun createCompilerJvmOptions(): KotlinJvmCompilerOptions {
        return myProject.objects.KotlinJvmCompilerOptionsDefault(myProject)
    }

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        message = "Replaced by compilerJvmOptions",
        replaceWith = ReplaceWith("createCompilerJvmOptions()"),
        level = DeprecationLevel.ERROR,
    )
    override fun createKotlinJvmOptions(): KotlinJvmOptions {
        return object : KotlinJvmOptions {
            @OptIn(org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi::class)
            @Deprecated(
                message = org.jetbrains.kotlin.gradle.dsl.KOTLIN_OPTIONS_DEPRECATION_MESSAGE,
                level = DeprecationLevel.ERROR,
            )
            override val options: KotlinJvmCompilerOptions = createCompilerJvmOptions()
        }
    }

    @Deprecated("Use API to create specific Kotlin extensions such as 'createKotlinJvmExtension()' or 'createKotlinAndroidExtension()'")
    override val kotlinExtension: KotlinProjectExtension by lazy {
        myProject.objects.newInstance(KotlinProjectExtension::class.java, myProject)
    }

    override fun createKotlinJvmExtension(): KotlinJvmExtension {
        return myProject.objects.newInstance(KotlinJvmProjectExtension::class.java, myProject).also { extension ->
            val target = myProject.objects.KotlinWithJavaTargetForJvm(myProject)
            extension.targetFuture.complete(target)
        }
    }

    override fun createKotlinAndroidExtension(): KotlinAndroidExtension {
        return myProject.objects.newInstance(KotlinAndroidProjectExtension::class.java, myProject).also { extension ->
            val target = myProject.objects.KotlinAndroidTarget(myProject)
            extension.targetFuture.complete(target)
        }
    }

    override val kaptExtension: KaptExtension by lazy {
        myProject.objects.newInstance(KaptExtension::class.java)
    }

    @Deprecated("Replaced with 'registerKotlinJvmCompileTask(taskName, compilerOptions, explicitApiMode)'")
    override fun registerKotlinJvmCompileTask(taskName: String): TaskProvider<out KotlinJvmCompile> {
        @Suppress("DEPRECATION") val extension = kotlinExtension
        return registerKotlinJvmCompileTask(
            taskName,
            createCompilerJvmOptions(),
            providerFactory.provider { extension.explicitApi }
        )
    }

    @Deprecated("Replaced with 'registerKotlinJvmCompileTask(taskName, compilerOptions, explicitApiMode)'")
    override fun registerKotlinJvmCompileTask(taskName: String, moduleName: String): TaskProvider<out KotlinJvmCompile> {
        val compilerOptions = createCompilerJvmOptions()
        compilerOptions.moduleName.convention(moduleName)
        @Suppress("DEPRECATION") val extension = kotlinExtension
        return registerKotlinJvmCompileTask(
            taskName,
            compilerOptions,
            providerFactory.provider { extension.explicitApi }
        )
    }

    override fun registerKotlinJvmCompileTask(
        taskName: String,
        compilerOptions: KotlinJvmCompilerOptions,
        explicitApiMode: Provider<ExplicitApiMode>,
    ): TaskProvider<out KotlinJvmCompile> {
        val taskCompilerOptions = createCompilerJvmOptions()
        KotlinJvmCompilerOptionsHelper.syncOptionsAsConvention(compilerOptions, taskCompilerOptions)
        val registeredKotlinJvmCompileTask = taskCreator.registerKotlinJVMTask(
            myProject,
            taskName,
            taskCompilerOptions,
            KotlinCompileConfig(
                myProject,
                explicitApiMode,
            )
        )
        registeredKotlinJvmCompileTask.configure {
            @Suppress("DEPRECATION_ERROR")
            it.moduleName.set(taskCompilerOptions.moduleName)
        }
        return registeredKotlinJvmCompileTask
    }

    @Deprecated("Replaced with 'registerKaptGenerateStubsTask(taskName, compileTask, kaptExtension, explicitApiMode)'")
    override fun registerKaptGenerateStubsTask(taskName: String): TaskProvider<out KaptGenerateStubs> {
        val taskConfig = KaptGenerateStubsConfig(
            myProject,
            providerFactory.provider {
                @Suppress("DEPRECATION")
                kotlinExtension.explicitApi
            },
            kaptExtension
        )
        return myProject.registerTask(taskName, KaptGenerateStubsTask::class.java, listOf(myProject)).also {
            taskConfig.execute(it)
        }
    }

    override fun registerKaptGenerateStubsTask(
        taskName: String,
        compileTask: TaskProvider<out KotlinJvmCompile>,
        kaptExtension: KaptExtensionConfig,
        explicitApiMode: Provider<ExplicitApiMode>
    ): TaskProvider<out KaptGenerateStubs> {
        val taskConfig = KaptGenerateStubsConfig(
            myProject,
            explicitApiMode,
            kaptExtension
        )

        val kaptGenerateStubsTask =  myProject.registerTask(
            taskName,
            KaptGenerateStubsTask::class.java,
            listOf(myProject)
        )

        taskConfig.execute(kaptGenerateStubsTask)

        kaptGenerateStubsTask.configure {
            val compileTaskCompilerOptions = compileTask.get().compilerOptions
            KaptGenerateStubsConfig.syncOptionsFromCompileTask(compileTaskCompilerOptions, it)
        }

        return kaptGenerateStubsTask
    }

    @Deprecated("Replaced with 'registerKaptTask(taskName, kaptExtension)'")
    override fun registerKaptTask(taskName: String): TaskProvider<out Kapt> {
        return registerKaptTask(taskName, kaptExtension)
    }

    override fun registerKaptTask(
        taskName: String,
        kaptExtension: KaptExtensionConfig,
    ): TaskProvider<out Kapt> {
        val kaptTaskConfiguration = KaptWithoutKotlincConfig(myProject, kaptExtension)
        return myProject.registerTask(taskName, KaptWithoutKotlincTask::class.java, emptyList()).also {
            kaptTaskConfiguration.execute(it)
        }
    }

    // Required for AGP/Built-in Kotlin integration
    // ABI preferably should not change
    /**
     * Creates a Kotlin Android compilation instance for a specified Android variant.
     *
     * Usage example:
     * ```
     * val androidExtension = apiPlugin.createKotlinAndroidExtension()
     * project.extensions.add("kotlin", androidExtension)
     * val compilation = apiPlugin.createKotlinAndroidCompilation(
     *     variant.name,
     *     androidExtension.target as KotlinAndroidTarget,
     *     variant.javaCompileTask,
     *     AndroidVariantType.Main
     * )
     * // Kotlin compile task is not auto-created by compilation and has to be created manually
     * val kotlinCompileTask = apiPlugin.registerKotlinJvmCompileTask(
     *     compilation.compileKotlinTaskName,
     *     plugin.createCompilerJvmOptions(),
     *     project.provider { ExplicitApiMode.Disabled }
     * )
     * ```
     *
     * @param name the name of the compilation. Usually should be the same as a variant name.
     * @param androidTarget the target associated with the Android project where the compilation is being created.
     * @param androidVariantJavaCompileTask the Java compile task associated with the given Android variant.
     * @param androidVariantType the type of the Android variant (e.g., main code, test code, etc).
     * @return a new instance of [KotlinJvmAndroidCompilation] configured for the specified parameters.
     */
    @InternalKotlinGradlePluginApi
    fun createKotlinAndroidCompilation(
        name: String,
        androidTarget: KotlinAndroidTarget,
        androidVariantJavaCompileTask: TaskProvider<JavaCompile>,
        androidVariantType: AndroidVariantType,
    ): KotlinJvmAndroidCompilation {
        val compilationFactory = KotlinJvmAgpCompilationFactory(
            androidVariantJavaCompileTask,
            androidVariantType,
            androidTarget,
        )

        return compilationFactory.create(name)
    }
}