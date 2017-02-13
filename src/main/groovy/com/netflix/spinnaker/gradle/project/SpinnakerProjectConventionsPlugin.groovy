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

package com.netflix.spinnaker.gradle.project

import com.jfrog.bintray.gradle.BintrayExtension
import com.netflix.spinnaker.gradle.ospackage.OspackageBintrayExtension
import com.netflix.spinnaker.gradle.ospackage.OspackageBintrayPublishPlugin
import nebula.plugin.info.scm.ScmInfoExtension
import nebula.plugin.netflixossproject.NetflixOssProjectPlugin
import nebula.plugin.netflixossproject.publishing.PublishingPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention

class SpinnakerProjectConventionsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(NetflixOssProjectPlugin)

        project.plugins.withType(JavaPlugin) {
            JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
            convention.sourceCompatibility = JavaVersion.VERSION_1_8
            convention.targetCompatibility = JavaVersion.VERSION_1_8
        }

        Closure<String> propOrDefault = { String propertyName, String defaultValue ->
            project.hasProperty(propertyName) ? project.property(propertyName) : defaultValue
        }

        String bintrayOrg = propOrDefault('bintrayOrg', 'spinnaker')
        String bintrayJarRepo = propOrDefault('bintrayJarRepo', 'spinnaker')
        ScmInfoExtension scmInfo = project.extensions.getByType(ScmInfoExtension)
        String projectUrl = PublishingPlugin.calculateUrlFromOrigin(scmInfo.origin)
        String issuesUrl = "$projectUrl/issues"
        String vcsUrl = "${projectUrl}.git"

        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        bintray.pkg.userOrg = bintrayOrg
        bintray.pkg.repo = bintrayJarRepo
        bintray.pkg.labels = ['Spinnaker', 'Netflix', 'netflixoss']
        bintray.pkg.websiteUrl = projectUrl
        bintray.pkg.issueTrackerUrl = issuesUrl
        bintray.pkg.vcsUrl = vcsUrl

        project.logger.info("Set bintray project URL to ${projectUrl}")

        project.plugins.withType(OspackageBintrayPublishPlugin) {
            OspackageBintrayExtension bintrayPackage = (OspackageBintrayExtension) project.extensions.getByName('bintrayPackage')
            bintrayPackage.packageRepo = propOrDefault('bintrayPackageRepo', 'debians')
            bintrayPackage.debDistribution = 'trusty'
            bintrayPackage.debComponent = 'spinnaker'
            bintrayPackage.debArchitectures = 'i386,amd64'
            bintrayPackage.buildNumber = propOrDefault('bintrayPackageBuildNumber', '')
        }

        project.repositories.jcenter()
        project.repositories.maven { MavenArtifactRepository repo ->
            repo.name = 'Bintray Spinnaker repo'
            //TODO url matching org + jarRepo
            repo.url = "https://dl.bintray.com/$bintrayOrg/$bintrayJarRepo"
        }
    }
}
