/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import gradle.kotlin.dsl.accessors._1859e2a6ff8d0a87dae202f489fb97f5.apiDependenciesMetadata
import gradle.kotlin.dsl.accessors._1859e2a6ff8d0a87dae202f489fb97f5.apiElements
import gradle.kotlin.dsl.accessors._90dfa3f8a3f5813686c8ef8a8b413b1a.commonMainApi
import gradle.kotlin.dsl.accessors._90dfa3f8a3f5813686c8ef8a8b413b1a.commonMainApiDependenciesMetadata
import gradle.kotlin.dsl.accessors._90dfa3f8a3f5813686c8ef8a8b413b1a.commonMainImplementation
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import util.*

val isGradlePlugin = project.name == "gradle-plugin"
val publishingExtension = project.extensions.findByType<PublishingExtension>()
val globalRootDir: String by extra

if (isPublicModule) {
    if (publishingExtension == null) {
        apply(plugin = "maven-publish")
    }

    if (project.getSensitiveProperty("libs.sign.key.private") != null) {
        apply(plugin = "signing")
    }

    if(project.name=="bom"){
        // 定义一个新的配置用于 metadataApiElements 变体
        project.afterEvaluate {
            project.components.withType<SoftwareComponentInternal>().configureEach {
                if(this is AdhocComponentWithVariants){
                    // 添加共用variant
                    val dependency = configurations.apiElements.get().allDependencyConstraints
                    /**
                     * "name": "metadataApiElements",
                     *       "attributes": {
                     *         "org.gradle.category": "library",
                     *         "org.gradle.jvm.environment": "non-jvm",
                     *         "org.gradle.usage": "kotlin-metadata",
                     *         "org.jetbrains.kotlin.platform.type": "common"
                     *       },
                     */
                    addVariantsFromConfiguration(configurations.create("metadataApiElements") {
                        this.attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.common)
                        this.attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named("library"))
                        this.attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named("non-jvm"))
                        this.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("kotlin-metadata"))
                        this.dependencyConstraints.addAll(dependency)
                    }) {

                    }
                }
            }
        }
    }
    the<PublishingExtension>().configurePublication()
    logger.info("Configured ${project.name} for publication")
} else {
    logger.info("Skipping ${project.name} publication configuration, not a public module")
}

fun PublishingExtension.configurePublication() {
    repositories {
//        configureSonatypeRepository()
//        configureSpaceEapRepository()
//        configureSpaceGrpcRepository()
//        configureForIdeRepository()
        configureLocalDevRepository()
    }

    configureJvmPublicationIfNeeded()

    val javadocJar = if (!isGradlePlugin) {
        configureEmptyJavadocArtifact()
    } else {
        null
    }

    publications.withType(MavenPublication::class).all {
        pom.configureMavenCentralMetadata()
//        signPublicationIfKeyPresent()
        if (javadocJar != null) {
            artifact(javadocJar)
        }

        // mainly for kotlinMultiplatform publication
        setPublicArtifactId(project)

        if (!isGradlePlugin) {
            fixModuleMetadata(project)
            println("isNotGradlePlugin==${project.name} ${project.path}")
        }else{
            println("isGradlePlugin==${project.name} ${project.path}")
        }

        logger.info("Project ${project.name} -> Publication configured: $name, $version")
    }

    tasks.withType<PublishToMavenRepository>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}

// we need to configure maven publication for kotlin("jvm") projects manually
fun PublishingExtension.configureJvmPublicationIfNeeded() {
    if (isGradlePlugin) {
        return
    }

    project.withKotlinJvmExtension {
        if (publications.isNotEmpty()) {
            return@withKotlinJvmExtension
        }

        logger.info("Manually added maven publication to ${project.name}")
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}

fun MavenPom.configureMavenCentralMetadata() {
    name by project.name
    description by "kotlinx.rpc, a Kotlin library for adding asynchronous RPC services to your applications."
    url by "https://github.com/Kotlin/kotlinx-rpc"

    licenses {
        license {
            name by "The Apache Software License, Version 2.0"
            url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "JetBrains"
            name by "JetBrains Team"
            organization by "JetBrains"
            organizationUrl by "https://www.jetbrains.com"
        }
    }

    scm {
        url by "https://github.com/Kotlin/kotlinx-rpc"
        connection by "scm:git:git://github.com/Kotlin/kotlinx-rpc.git"
        developerConnection by "scm:git:git@github.com:Kotlin/kotlinx-rpc.git"
    }
}

fun RepositoryHandler.configureSpaceEapRepository() {
    configureRepository(project) {
        username = "SPACE_USERNAME"
        password = "SPACE_PASSWORD"
        name = "space"
        url = "https://maven.pkg.jetbrains.space/public/p/krpc/eap"
    }
}

fun RepositoryHandler.configureSpaceGrpcRepository() {
    configureRepository(project) {
        username = "SPACE_USERNAME"
        password = "SPACE_PASSWORD"
        name = "grpc"
        url = "https://maven.pkg.jetbrains.space/public/p/krpc/grpc"
    }
}

fun RepositoryHandler.configureForIdeRepository() {
    configureRepository(project) {
        username = "SPACE_USERNAME"
        password = "SPACE_PASSWORD"
        name = "forIde"
        url = "https://maven.pkg.jetbrains.space/public/p/krpc/for-ide"
    }
}

fun RepositoryHandler.configureLocalDevRepository() {
    // Something that's straightforward to "clean" for development, not mavenLocal
    maven("$globalRootDir/maven/myRepo") {
        name = "buildRepo"
    }
}

fun RepositoryHandler.configureSonatypeRepository() {
    configureRepository(project) {
        username = "libs.sonatype.user"
        password = "libs.sonatype.password"
        name = "sonatype"
        url = sonatypeRepositoryUri
    }
}

val sonatypeRepositoryUri: String?
    get() {
        val repositoryId: String = project.getSensitiveProperty("libs.repository.id")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId"
    }

fun configureEmptyJavadocArtifact(): org.gradle.jvm.tasks.Jar {
    val javadocJar by project.tasks.creating(Jar::class) {
        archiveClassifier.set("javadoc")
        // contents are deliberately left empty
        // https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
    }
    return javadocJar
}

fun MavenPublication.signPublicationIfKeyPresent() {
    val keyId = project.getSensitiveProperty("libs.sign.key.id")
    val signingKey = project.getSensitiveProperty("libs.sign.key.private")
    val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")

    if (!signingKey.isNullOrBlank()) {
        the<SigningExtension>().apply {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)

            sign(this@signPublicationIfKeyPresent)
        }
    }
}
