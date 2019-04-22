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

package com.netflix.spinnaker.gradle.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import com.netflix.spinnaker.gradle.baseproject.SpinnakerBaseProjectConventionsPlugin
import com.netflix.spinnaker.gradle.ospackage.OspackageBintrayExtension
import com.netflix.spinnaker.gradle.ospackage.OspackageBintrayPublishPlugin
import nebula.core.ProjectType
import nebula.plugin.info.scm.ScmInfoExtension
import nebula.plugin.netflixossproject.NetflixOssProjectPlugin
import nebula.plugin.netflixossproject.publishing.PublishingPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention

class SpinnakerBintrayPublishingConventionsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(NetflixOssProjectPlugin)

        //undo the NetflixOssProjectPlugin's Java 1.7 opinion:
        ProjectType type = new ProjectType(project)
        if (type.isLeafProject) {
            project.plugins.withType(JavaPlugin) { JavaPlugin javaPlugin ->
                JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
                convention.sourceCompatibility = JavaVersion.VERSION_1_8
            }
        }
        project.plugins.apply(SpinnakerBaseProjectConventionsPlugin)

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
            bintrayPackage.debDistribution = propOrDefault('bintrayPackageDebDistribution', 'trusty')
            bintrayPackage.debComponent = 'spinnaker'
            bintrayPackage.debArchitectures = 'i386,amd64'
            bintrayPackage.buildNumber = propOrDefault('bintrayPackageBuildNumber', '')
            bintrayPackage.publishWaitForSecs = propOrDefault('bintrayPublishWaitForSecs', '0').toInteger()
        }

        project.repositories.jcenter()
        project.repositories.maven { MavenArtifactRepository repo ->
            repo.name = 'Bintray Spinnaker repo'
            //TODO url matching org + jarRepo
            repo.url = "https://dl.bintray.com/$bintrayOrg/$bintrayJarRepo"
        }
    }
}
