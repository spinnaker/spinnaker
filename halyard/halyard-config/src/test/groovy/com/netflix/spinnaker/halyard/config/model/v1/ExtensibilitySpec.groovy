/*
 * Copyright 2019 Armory, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.config.model.v1.node.Extensibility;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginExtension
import spock.lang.Specification

class ExtensibilitySpec extends Specification {
    void "getPluginConfigurations works correctly"() {
        setup:
        def extensibility = new Extensibility()
        def plugin = new Plugin()
        plugin.enabled = true
        plugin.id = 'should-not-be-present'
        plugin.version = '1.2.3'
        plugin.extensions.put("test.plugin", new PluginExtension().setEnabled(true))
        def pluginsMap = [
                "Test.Plugin": plugin
        ]
        extensibility.setPlugins(pluginsMap)

        when:
        def subject = extensibility.pluginsConfig()

        then:
        def expectedOptions = [
                "Test.Plugin": [
                        'enabled': true,
                        'version': '1.2.3',
                        "config": [:],
                        'extensions': [
                                "test.plugin": [
                                        "enabled": true,
                                        "config": [:]
                                ]
                        ],
                ]
        ]
        subject == expectedOptions
    }
}
