/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.testplugin

/**
 * The root [GeneratedTestPlugin] builder function.
 */
fun testPlugin(init: GeneratedTestPlugin.() -> Unit): GeneratedTestPlugin =
  GeneratedTestPlugin().also(init)

/**
 * A basic generated plugin that will load, but not much else.
 *
 * @param pluginName The CapitalizedCase name of the plugin (can be null)
 */
fun basicGeneratedPlugin(pluginName: String? = null, version: String? = null): GeneratedTestPlugin =
  testPlugin {
    if (pluginName != null) {
      name = pluginName.capitalize()
    }

    if (version != null) {
      this.version = version
    }

    sourceFile(
      "${name}Extension",
      """
        package $packageName;

        import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension;
        import org.pf4j.Extension;

        @Extension
        public class {{simpleName}} implements TestExtension {

          public ${name}PluginConfiguration config;

          public {{simpleName}}(${name}PluginConfiguration config) {
            config = config;
          }

          @Override
          public String getTestValue() {
            return getClass().getSimpleName();
          }
        }
      """.trimIndent()
    )

    sourceFile(
      "${name}PluginConfiguration",
      """
        package $packageName;

        import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration;

        @PluginConfiguration("$pluginId")
        public class {{simpleName}} {
          private String foo;
        }
      """.trimIndent()
    )
  }
