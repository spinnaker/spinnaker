/*
 * Copyright 2019 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.netflix.spinnaker.gradle.extension

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit test for the 'com.netflix.spinnaker.gradle.extension.spinnakerextension' plugin.
 */
@Ignore
class SpinnakerExtensionGradlePluginTest {

    @Test fun `spinnakerserviceextension plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.spinnaker.plugin.service-extension")
        project.plugins.apply("java")

        // Verify the result
        assertNotNull(project.tasks.findByName("registerPlugin"))
        assertNotNull(project.tasks.findByName("assemblePluginZip"))
    }

    @Test fun `spinnakeruiextension plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.spinnaker.plugin.ui-extension")

        // Verify the result
        assertNotNull(project.tasks.findByName("registerPlugin"))
        assertNotNull(project.tasks.findByName("assemblePluginZip"))
    }

    @Test fun `spinnakerextensionbundler plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("io.spinnaker.plugin.bundler")
        // Verify the result
        assertNotNull(project.tasks.findByName("distPluginZip"))
    }

}
