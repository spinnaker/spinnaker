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

import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException
import org.springframework.boot.liquibase.SpringPackageScanClassResolver
import org.springframework.core.SpringProperties
import spock.lang.Specification

class PluginSpec extends Specification {

    void "plugin merge works correctly"() {
        setup:
        def originalOptions = [
                example: [
                        url: 'host',
                        port: 1000,
                        nested: [
                                foo: 'bar',
                                key: 'value'
                        ],
                        list: [
                                'first',
                                'second'
                        ],
                        changeMe: [
                                'change',
                                'to',
                                'integer'
                        ],
                        doNot: 'change'
                ]
        ]

        def newOptions = [
                example: [
                        url: 'new.host',
                        port: 2000,
                        nested: [
                                foo: 'baz',
                                cat: 'dog'
                        ],
                        list: [
                                'new',
                                'list'
                        ],
                        changeMe: 200
                ]
        ]

        when:
        def subject = Plugin.merge(originalOptions, newOptions)

        then:
        def expectedOptions = [
                example: [
                        url: 'new.host',
                        port: 2000,
                        nested: [
                                foo: 'baz',
                                cat: 'dog',
                                key: 'value'
                        ],
                        list: [
                                'first',
                                'second',
                                'new',
                                'list'
                        ],
                        changeMe: 200,
                        doNot: 'change'
                ]
        ]
        subject == expectedOptions
    }
}
