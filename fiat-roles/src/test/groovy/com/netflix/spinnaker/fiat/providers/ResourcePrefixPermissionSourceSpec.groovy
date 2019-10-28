/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.fiat.providers

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Application
import spock.lang.Specification

class ResourcePrefixPermissionSourceSpec extends Specification {

    def "should aggregate permissions matching a resource correctly if resolution strategy is aggregate"() {
        given:
        def source = new ResourcePrefixPermissionSource<Application>().setPrefixes([
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('*').setPermissions([
                        (Authorization.CREATE): ['admins']
                ]),
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('gotham*').setPermissions([
                        (Authorization.CREATE): ['police']
                ]),
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('gotham-joker').setPermissions([
                        (Authorization.CREATE): ['batman']
                ]),
        ]).setResolutionStrategy(ResourcePrefixPermissionSource.ResolutionStrategy.AGGREGATE)

        when:
        def application = new Application().setName(applicationName)

        then:
        source.getPermissions(application).get(Authorization.CREATE) as Set == expectedCreatePermissions as Set

        where:
        applicationName     | expectedCreatePermissions
        'new york'          | ["admins"]
        'gotham-criminals'  | ["admins", "police"]
        'gotham-joker'      | ["admins", "police", "batman"]
    }

    def "should apply the most specific permissions matching a resource if resolution strategy is most_specific"() {
        given:
        def source = new ResourcePrefixPermissionSource<Application>().setPrefixes([
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('*').setPermissions([
                        (Authorization.CREATE): ['admins']
                ]),
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('gotham*').setPermissions([
                        (Authorization.CREATE): ['police']
                ]),
                new ResourcePrefixPermissionSource.PrefixEntry<Application>().setPrefix('gotham-joker').setPermissions([
                        (Authorization.CREATE): ['batman']
                ]),
        ]).setResolutionStrategy(ResourcePrefixPermissionSource.ResolutionStrategy.MOST_SPECIFIC)

        when:
        def application = new Application().setName(applicationName)

        then:
        source.getPermissions(application).get(Authorization.CREATE) as Set == expectedCreatePermissions as Set

        where:
        applicationName     | expectedCreatePermissions
        'new york'          | ["admins"]
        'gotham-criminals'  | ["police"]
        'gotham-joker'      | ["batman"]
    }
}
