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
import com.jfrog.bintray.gradle.Utils
import com.netflix.gradle.plugins.deb.Deb
import groovy.json.JsonBuilder
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Upload

import static groovyx.net.http.ContentType.BINARY
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.HEAD
import static groovyx.net.http.Method.POST
import static groovyx.net.http.Method.PUT

/**
 * Big copy-n-paste of BintrayUploadTask that does the right thing for deb repo uploads
 */
class OspackageBintrayPublishPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(BintrayPlugin)

        def packageExtension = project.extensions.create('bintrayPackage', OspackageBintrayExtension)

        project.tasks.withType(Deb) { Deb deb ->
            deb.release = packageExtension.buildNumber
            String debTaskName = "${Character.toUpperCase(deb.name.charAt(0))}${deb.name.substring(1)}"
            def publishDeb = project.tasks.create("publish$debTaskName")
            publishDeb.doFirst {
                BintrayExtension bintrayCfg = project.extensions.getByType(BintrayExtension)
                def http = BintrayHttpClientFactory.create(bintrayCfg.apiUrl, bintrayCfg.user, bintrayCfg.key)
                def org = bintrayCfg.pkg.userOrg ?: bintrayCfg.user
                def repoName = packageExtension.packageRepo ?: bintrayCfg.pkg.repo
                def repoPath = "$org/$repoName"
                def packageName = deb.packageName
                def packagePath = "$repoPath/$packageName"
                String versionName = bintrayCfg.pkg.version.name ?: project.version.toString()
                if (versionName.endsWith('-SNAPSHOT')) {
                    versionName = versionName.replaceAll(/SNAPSHOT/, Long.toString(System.currentTimeMillis()))
                }

                def setAttributes = { attributesPath, attributes, entity, entityName ->
                    http.request(POST, JSON) {
                        uri.path = attributesPath
                        def builder = new JsonBuilder()
                        builder.content = attributes.collect {
                            //Support both arrays and singular values - coerce to an array of values
                            ['name': it.key, 'values': [it.value].flatten()]
                        }
                        body = builder.toString()
                        response.success = { resp ->
                            project.logger.info("Attributes set on $entity '$entityName'.")
                        }
                        response.failure = { resp, reader ->
                            throw new GradleException(
                                    "Could not set attributes on $entity '$entityName': $resp.statusLine $reader")
                        }
                    }
                }

                //create package if needed
                def createPackageIfNeeded = {
                    def createPackage = false
                    http.request(HEAD) {
                        uri.path = "/packages/$packagePath"
                        response.success = { resp ->
                            project.logger.debug("Package '$packageName' exists.")
                        }
                        response.'404' = { resp ->
                            project.logger.info("Package '$packageName' does not exist. Attempting to creating it...")
                            createPackage = true
                        }
                    }
                    if (createPackage) {
                        if (bintrayCfg.dryRun) {
                            logger.info("(Dry run) Created pakage '$packagePath'.")
                            return
                        }
                        http.request(POST, JSON) {
                            uri.path = "/packages/$repoPath"
                            body = [name                   : packageName, desc: bintrayCfg.pkg.desc, licenses: bintrayCfg.pkg.licenses, labels: bintrayCfg.pkg.labels,
                                    website_url            : bintrayCfg.pkg.websiteUrl, issue_tracker_url: bintrayCfg.pkg.websiteUrl, vcs_url: bintrayCfg.pkg.vcsUrl,
                                    public_download_numbers: bintrayCfg.pkg.publicDownloadNumbers]

                            response.success = { resp ->
                                project.logger.info("Created package '$packagePath'.")
                            }
                            response.failure = { resp, reader ->
                                throw new GradleException("Could not create package '$packagePath': $resp.statusLine $reader")
                            }
                        }
                        if (bintrayCfg.pkg.attributes) {
                            setAttributes "/packages/$packagePath/attributes", bintrayCfg.pkg.attributes, 'package', packageName
                        }
                    }
                }

                //create version if needed
                def createVersionIfNeeded = {
                    def createVersion
                    http.request(HEAD) {
                        uri.path = "/packages/$packagePath/versions/$versionName"
                        response.success = { resp ->
                            project.logger.debug("Version '$packagePath/$versionName' exists.")
                        }
                        response.'404' = { resp ->
                            project.logger.info("Version '$packagePath/$versionName' does not exist. Attempting to creating it...")
                            createVersion = true
                        }
                    }
                    if (createVersion) {
                        if (bintrayCfg.dryRun) {
                            logger.info("(Dry run) Created verion '$packagePath/$versionName'.")
                            return
                        }
                        http.request(POST, JSON) {
                            uri.path = "/packages/$packagePath/versions"
                            def versionReleased = Utils.toIsoDateFormat(bintrayCfg.pkg.version.released)
                            body = [name: versionName, desc: bintrayCfg.pkg.version.desc, released: versionReleased, vcs_tag: bintrayCfg.pkg.version.vcsTag]
                            response.success = { resp ->
                                logger.info("Created version '$versionName'.")
                            }
                            response.failure = { resp, reader ->
                                throw new GradleException("Could not create version '$versionName': $resp.statusLine $reader")
                            }
                        }
                        if (bintrayCfg.pkg.version.attributes) {
                            setAttributes "/packages/$packagePath/versions/$versionName/attributes", bintrayCfg.pkg.version.attributes,
                                    'version', versionName
                        }
                    }
                }

                def uploadArtifact = {
                    if (!deb.archivePath.exists()) {
                        project.logger.error("Not uploading missing file $deb.archivePath")
                        return
                    }
                    def debFileName = deb.archivePath.name
                    def poolPath = "pool/main/${packageName.charAt(0)}/$packageName"
                    def versionPath = "$packagePath/$versionName"
                    def uploadUri = "/content/$versionPath/$poolPath/$debFileName;deb_distribution=$packageExtension.debDistribution;deb_component=$packageExtension.debComponent;deb_architecture=$packageExtension.debArchitectures"
                    def fullUri = "$bintrayCfg.apiUrl$uploadUri"
                    deb.archivePath.withInputStream { is ->
                        is.metaClass.totalBytes = {
                            deb.archivePath.length()
                        }
                        project.logger.info("Uploading to $fullUri...")
                        if (bintrayCfg.dryRun) {
                            project.logger.info("(Dry run) Uploaded to '$fullUri'.")
                            return
                        }
                        http.request(PUT) {
                            uri.path = uploadUri
                            requestContentType = BINARY
                            body = is
                            response.success = { resp ->
                                project.logger.info("Uploaded to '$fullUri'.")
                            }
                            response.failure = { resp, reader ->
                                throw new GradleException("Could not upload to '$fullUri': $resp.statusLine $reader")
                            }
                        }
                    }
                }

                def gpgSignVersion = {
                    if (bintrayCfg.dryRun) {
                        logger.info("(Dry run) Signed verion '$packagePath/$versionName'.")
                        return
                    }
                    http.request(POST, JSON) {
                        uri.path = "/gpg/$packagePath/versions/$versionName"
                        if (bintrayCfg.pkg.version.gpg.passphrase) {
                            body = [passphrase: bintrayCfg.pkg.version.gpg.passphrase]
                        }
                        response.success = { resp ->
                            project.logger.info("Signed version '$versionName'.")
                        }
                        response.failure = { resp, reader ->
                            throw new GradleException("Could not sign version '$versionName': $resp.statusLine $reader")
                        }
                    }
                }

                def publishVersion = {
                    def publishUri = "/content/$packagePath/$versionName/publish"
                    if (bintrayCfg.dryRun) {
                        logger.info("(Dry run) Pulished verion '$packagePath/$versionName'.")
                        return
                    }
                    http.request(POST, JSON) {
                        uri.path = publishUri
                        response.success = { resp ->
                            project.logger.info("Published '$packagePath/$versionName'.")
                        }
                        response.failure = { resp, reader ->
                            throw new GradleException("Could not publish '$packagePath/$versionName': $resp.statusLine $reader")
                        }
                    }
                }

                createPackageIfNeeded()
                createVersionIfNeeded()
                uploadArtifact()
                gpgSignVersion()
                publishVersion()
            }

            publishDeb.mustRunAfter('build')
            publishDeb.dependsOn(deb)
            Upload installTask = project.tasks.withType(Upload)?.findByName('install')
            if (installTask) {
                publishDeb.dependsOn(installTask)
            }
            publishDeb.group = BintrayUploadTask.GROUP
            project.rootProject.tasks.release.dependsOn(publishDeb)
            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                publishDeb.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate') || (project.hasProperty("forcePublish$debTaskName") && project.property("forcePublish$debTaskName") as Boolean)
                }
            }
        }
    }
}
