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
package com.netflix.spinnaker.gradle.extension.tasks

import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

/**
 * Task to assemble plugin related files(dependency jars, class files etc) into a zip.
 */
open class AssembleJavaPluginZipTask : Jar() {

    init {
        project.afterEvaluate {
            archiveBaseName.set(project.name)
            archiveExtension.set("zip")
            val jar = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)
            this.dependsOn(jar)
            val childSpec: CopySpec = project.copySpec().with(this).into("classes")
            val libSpec: CopySpec = project.copySpec().from(project.configurations.getByName("runtimeClasspath")).into("lib")
            this.with(childSpec, libSpec)
        }
    }

}
