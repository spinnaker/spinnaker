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

package com.netflix.spinnaker.halyard.config.model.v1.plugins

import com.netflix.spinnaker.halyard.config.model.v1.node.Plugins
import spock.lang.Specification

class PluginsSpec extends Specification {

    void "getPluginConfigurations works correctly"() {
        setup:
        def plugins = new Plugins()
        def plugin = Spy(Plugin)
        def manifest = Stub(Manifest)
        manifest.name >> 'namespace/name'
        manifest.options >> [foo: 'bar']
        plugin.manifestLocation >> 'manifest-location'
        plugin.enabled >> true
        plugin.options >> [cat: 'dog']
        plugin.name >> 'should-not-be-present'
        plugins.setPlugins([plugin])

        plugin.generateManifest() >> manifest

        when:
        def subject = plugins.pluginConfigurations()

        then:
        def expectedOptions = [
                plugins: [
                        'namespace/name': [
                                foo: 'bar',
                                cat: 'dog'
                        ]
                ]
        ]
        subject == expectedOptions
    }
}
