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

import com.netflix.spinnaker.gradle.publishing.SpinnakerBintrayPublishingPlugin
import nebula.plugin.netflixossproject.NetflixOssProjectPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention

class SpinnakerProjectPlugin implements Plugin<Gradle> {

    @Override
    void apply(Gradle gradle) {
        gradle.rootProject { project ->
            project.plugins.apply(NetflixOssProjectPlugin)
            project.plugins.apply(SpinnakerBintrayPublishingPlugin)

            //c&p this because NetflixOss reverts it to 1.7 and ends up getting applied last..
            project.plugins.withType(JavaBasePlugin) {
                JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
                convention.sourceCompatibility = JavaVersion.VERSION_1_8
                convention.targetCompatibility = JavaVersion.VERSION_1_8
            }
        }
    }
}
