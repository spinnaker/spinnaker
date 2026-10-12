/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.names

import com.netflix.spinnaker.moniker.Moniker
import spock.lang.Specification

class EcsServerGroupNameSpec extends Specification {

  def "versioned (classic ecs) name appends the vNNN sequence suffix"() {
    given:
    def moniker = Moniker.builder()
        .app("pshellobulls")
        .stack("production")
        .detail("native")
        .sequence(0)
        .build()

    when:
    def name = new EcsServerGroupName(moniker)

    then:
    !name.isFixedName()
    name.getFamilyName() == "pshellobulls-production-native"
    name.getServiceName() == "pshellobulls-production-native-v000"
    name.getContainerName() == "v000"
  }

  def "fixed-name (ecs-native) service name is the bare family name with no vNNN suffix"() {
    given:
    // A fixed-name server group carries no sequence.
    def moniker = Moniker.builder()
        .app("pshellobulls")
        .stack("production")
        .detail("native")
        .build()

    when:
    def name = new EcsServerGroupName(moniker, true)

    then:
    name.isFixedName()
    name.getFamilyName() == "pshellobulls-production-native"
    name.getServiceName() == "pshellobulls-production-native"
  }

  def "fixed-name getContainerName does not NPE on a null sequence and falls back to the family name"() {
    given:
    def moniker = Moniker.builder()
        .app("pshellobulls")
        .stack("production")
        .detail("native")
        .build()

    when:
    def name = new EcsServerGroupName(moniker, true)

    then:
    // Callers in the shared create path still invoke getContainerName(); it must be non-null and
    // must not format a null sequence.
    name.getContainerName() == "pshellobulls-production-native"
  }

  def "a versioned name with a null sequence still avoids NPE via the family-name fallback"() {
    given:
    def moniker = Moniker.builder()
        .app("pshellobulls")
        .stack("production")
        .detail("native")
        .build()

    when:
    def name = new EcsServerGroupName(moniker)

    then:
    name.getContainerName() == "pshellobulls-production-native"
  }
}
