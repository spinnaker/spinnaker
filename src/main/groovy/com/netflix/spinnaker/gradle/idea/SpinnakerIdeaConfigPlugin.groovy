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

package com.netflix.spinnaker.gradle.idea

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.plugins.ide.idea.IdeaPlugin

class SpinnakerIdeaConfigPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(IdeaPlugin)
        project.plugins.withType(IdeaPlugin) { IdeaPlugin idea ->
            if (project.rootProject == project) {
                project.plugins.withType(JavaPlugin) {
                    JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
                    idea.model.project.jdkName = convention.sourceCompatibility
                    idea.model.project.languageLevel = convention.targetCompatibility
                }
                idea.model.project.vcs = 'Git'
                idea.model.project.ipr.withXml {
                    def node = it.asNode()
                    def parser = new XmlParser()
                    node.append(parser.parse('''
                        <component name="GradleSettings">
                          <option name="linkedExternalProjectsSettings">
                            <GradleProjectSettings>
                              <option name="distributionType" value="DEFAULT_WRAPPED" />
                              <option name="externalProjectPath" value="$PROJECT_DIR$" />
                              <option name="useAutoImport" value="true" />
                            </GradleProjectSettings>
                          </option>
                        </component>'''))
                }
            }
        }
    }
}
