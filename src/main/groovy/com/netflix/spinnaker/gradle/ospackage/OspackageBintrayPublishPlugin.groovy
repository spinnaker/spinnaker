/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gradle.ospackage

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayHttpClientFactory
import com.jfrog.bintray.gradle.BintrayPlugin
import com.jfrog.bintray.gradle.BintrayUploadTask
import com.jfrog.bintray.gradle.RecordingCopyTask
import com.netflix.gradle.plugins.deb.Deb
import groovy.transform.Canonical
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Upload
import org.gradle.util.ConfigureUtil

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

/**
 * This is a workaround for:
 * https://github.com/bintray/gradle-bintray-plugin/issues/84
 *
 * and as such should die-in-a-fire if that issue gets resolved
 */
class OspackageBintrayPublishPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(BintrayPlugin)

        def packageExtension = project.extensions.create('bintrayPackage', OspackageBintrayExtension)

        project.tasks.withType(Deb) { Deb deb ->
            def extension = (BintrayExtension) project.extensions.getByName('bintray')
            extension.filesSpec {
                from deb.archivePath
                into '/'
            }
            extension.publications = null
            extension.configurations = null
            String name = 'publish' + deb.name.charAt(0).toUpperCase() + deb.name.substring(1)
            def buildDebPublish = project.tasks.create(name, BintrayUploadTask) { BintrayUploadTask task ->
                task.with {
                    apiUrl = extension.apiUrl
                    user = extension.user
                    apiKey = extension.key
                    filesSpec = extension.filesSpec
                    publish = extension.publish
                    dryRun = extension.dryRun
                    userOrg = extension.pkg.userOrg ?: extension.user
                    repoName = packageExtension.packageRepo ?: extension.pkg.repo
                    packageName = extension.pkg.name
                    packageDesc = extension.pkg.desc
                    packageWebsiteUrl = extension.pkg.websiteUrl
                    packageIssueTrackerUrl = extension.pkg.issueTrackerUrl
                    packageVcsUrl = extension.pkg.vcsUrl
                    packageLicenses = extension.pkg.licenses
                    packageLabels = extension.pkg.labels
                    packageAttributes = extension.pkg.attributes
                    packagePublicDownloadNumbers = extension.pkg.publicDownloadNumbers
                    versionName = extension.pkg.version.name ?: project.version
                    versionDesc = extension.pkg.version.desc
                    versionReleased = extension.pkg.version.released
                    versionVcsTag = extension.pkg.version.vcsTag ?: project.version
                    versionAttributes = extension.pkg.version.attributes
                    signVersion = extension.pkg.version.gpg.sign
                    gpgPassphrase = extension.pkg.version.gpg.passphrase
                    syncToMavenCentral = false
                }
            }

            buildDebPublish.mustRunAfter('build')
            buildDebPublish.dependsOn(deb)
            Upload installTask = project.tasks.withType(Upload)?.findByName('install')
            if (installTask) {
                buildDebPublish.dependsOn(installTask)
            }
            buildDebPublish.group = BintrayUploadTask.GROUP
            project.rootProject.tasks.release.dependsOn(buildDebPublish)
            def publishAllVersions = project.rootProject.tasks.maybeCreate('publishAllBintrayVersions')
            publishAllVersions.doFirst {
                def publishes = project.tasks.findAll { it instanceof BintrayUploadTask }.collect { BintrayUploadTask task ->
                    new PubVer(task.userOrg, task.repoName, task.packageName, task.versionName, task.apiUrl, task.user, task.apiKey)
                }.unique()

                for (PubVer pubVer : publishes) {
                    def http = BintrayHttpClientFactory.create(pubVer.apiUrl, pubVer.apiUser, pubVer.apiKey)
                    String pubUri = "/content/$pubVer.org/$pubVer.repoName/$pubVer.packageName/$pubVer.version/publish"
                    http.request(POST, JSON) {
                        uri.path = pubUri
                        response.success = { resp ->
                            logger.info("Published '$pubUri'.")
                        }
                        response.failure = { resp, reader ->
                            throw new GradleException("Could not publish '$pkgPath/$versionName': $resp.statusLine $reader")
                        }
                    }

                }
            }
            publishAllVersions.dependsOn(buildDebPublish)
            project.rootProject.tasks.release.dependsOn(publishAllVersions)
            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                buildDebPublish.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate')
                }
                publishAllVersions.onlyIf {
                    graph.allTasks.find { it instanceof BintrayUploadTask && it.enabled } &&
                       (graph.hasTask(':final') || graph.hasTask(':candidate'))
                }
            }

        }
    }

    @Canonical
    private static class PubVer {
        String org
        String repoName
        String packageName
        String version
        String apiUrl
        String apiUser
        String apiKey
    }
}
