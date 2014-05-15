/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.bluespar

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.plugins.ide.idea.IdeaPlugin
import nebula.plugin.plugin.NebulaBintrayPublishingPlugin
import nebula.plugin.plugin.NebulaOJOPublishingPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import nebula.plugin.responsible.NebulaResponsiblePlugin
import com.jfrog.bintray.gradle.BintrayExtension
import release.GitReleasePluginConvention
import release.ReleasePlugin
import release.ReleasePluginConvention


class BluesparProjectPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.status = project.version.toString().endsWith('-SNAPSHOT')?'integration':'release'
        project.plugins.apply(NebulaResponsiblePlugin)

        addStandardRepos(project)
        addProvidedScope(project)
        configureBintray(project)
        tweakGeneratedPom(project)
        configureSnapshot(project)
        configureRelease(project)
    }

    void addStandardRepos(Project project) {
        project.repositories.jcenter()
        project.repositories.maven {
            name 'Bintray bluespar repo'
            url 'http://dl.bintray.com/bluespar/bluespar'
        }
    }

    void addProvidedScope(Project project) {
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(IdeaPlugin)

        def provided = project.configurations.maybeCreate('provided')
        project.sourceSets.main.compileClasspath += provided
        project.idea.module.scopes.COMPILE.plus += provided
    }

    void configureBintray(Project project) {
        project.plugins.apply(NebulaBintrayPublishingPlugin)
        project.plugins.apply(NebulaOJOPublishingPlugin)

        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        bintray.pkg.userOrg = 'bluespar'
        bintray.pkg.repo = 'bluespar'
        bintray.pkg.labels = ['bluespar', 'groovy', 'Netflix']
        project.tasks.publish.dependsOn('bintrayUpload')        
    }

    void tweakGeneratedPom(Project project) {
        String repoName = project.name
        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { basePlugin ->
            basePlugin.withMavenPublication { MavenPublication t ->
                t.pom.withXml(new Action<XmlProvider>() {
                    @Override
                    void execute(XmlProvider x) {
                        def root = x.asNode()
                        root.appendNode('url', "https://github.com/bluespar/${repoName}")
                        root.appendNode('scm').replaceNode {
                            scm {
                                url "scm:git://github.com/bluespar/${repoName}.git"
                                connection "scm:git://github.com/bluespar/${repoName}.git"
                            }
                        }
                    }
                })
            }
        }
    }

    void configureSnapshot(Project project) {
        project.tasks.matching { it.name == 'artifactoryPublish' }.all {
            project.tasks.create('snapshot').dependsOn(it)
        }
    }

    void configureRelease(Project project) {
        // Release, to be replaced by our own in the future.
        project.plugins.apply(ReleasePlugin)
        project.ext.'gradle.release.useAutomaticVersion' = "true"

        ReleasePluginConvention releaseConvention = project.convention.getPlugin(ReleasePluginConvention)
        releaseConvention.failOnUnversionedFiles = false
        releaseConvention.failOnCommitNeeded = false

        // We routinely release from different branches, which aren't master
        def gitReleaseConvention = (GitReleasePluginConvention) releaseConvention.git
        gitReleaseConvention.requireBranch = null
        gitReleaseConvention.pushToCurrentBranch = true

        def spawnBintrayUpload = project.task('spawnBintrayUpload', description: 'Create GradleBuild to run BintrayUpload to use a new version', group: 'Release', type: GradleBuild) {
            startParameter = project.getGradle().startParameter.newInstance()
            tasks = ['bintrayUpload']
        }
        project.tasks.createReleaseTag.dependsOn spawnBintrayUpload
    }
}
