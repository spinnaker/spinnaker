/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.*
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.GlobalKubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class KubernetesKindRegistrySpec extends Specification {
  static final Function<KubernetesKind, Optional<KubernetesKindProperties>> NOOP_CRD_LOOKUP = { k -> Optional.empty() }
  static final KubernetesApiGroup CUSTOM_API_GROUP =  KubernetesApiGroup.fromString("test")
  static final KubernetesKind CUSTOM_KIND = KubernetesKind.from("customKind", CUSTOM_API_GROUP)
  static final KubernetesKindProperties REPLICA_SET_PROPERTIES = KubernetesKindProperties.withDefaultProperties(KubernetesKind.REPLICA_SET)
  static final KubernetesKindProperties CUSTOM_KIND_PROPERTIES = KubernetesKindProperties.create(CUSTOM_KIND, true)

  final GlobalKubernetesKindRegistry globalRegistry = Mock(GlobalKubernetesKindRegistry)
  final KubernetesKindRegistry.Factory factory = new KubernetesKindRegistry.Factory(globalRegistry)

  void "getRegisteredKind returns kinds that have been registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = factory.create(NOOP_CRD_LOOKUP, ImmutableList.of(CUSTOM_KIND_PROPERTIES))
    KubernetesKindProperties result

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(_ as KubernetesKind) >> Optional.empty()
    result == CUSTOM_KIND_PROPERTIES
  }

  void "getRegisteredKind falls back to the global registry for kinds that are not registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = factory.create()
    KubernetesKindProperties result
    KubernetesKindProperties globalResult = KubernetesKindProperties.create(CUSTOM_KIND, false)

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(CUSTOM_KIND) >> Optional.of(globalResult)
    result == globalResult
  }

  void "getGlobalKinds returns all global kinds that are registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = factory.create(NOOP_CRD_LOOKUP, ImmutableList.of(CUSTOM_KIND_PROPERTIES))
    Collection<KubernetesKindProperties> kinds

    when:
    kinds = kindRegistry.getGlobalKinds()

    then:
    _ * globalRegistry.getRegisteredKinds() >> ImmutableList.of(REPLICA_SET_PROPERTIES)
    kinds.size() == 1
    kinds.contains(REPLICA_SET_PROPERTIES)
  }

  void "getRegisteredKind returns default properties for a kind that has not been registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = factory.create()
    KubernetesKindProperties result

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(CUSTOM_KIND) >> Optional.empty()
    result == KubernetesKindProperties.withDefaultProperties(CUSTOM_KIND)
  }

  void "getRegisteredKind attempts to look up properties of an unkown kind"() {
    given:
    Function<KubernetesKind, KubernetesKindProperties> supplier = Mock(Function)
    KubernetesKindProperties customProperties = KubernetesKindProperties.create(CUSTOM_KIND, false)
    @Subject KubernetesKindRegistry kindRegistry = factory.create(supplier, ImmutableList.of())
    KubernetesKindProperties result

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(CUSTOM_KIND) >> Optional.empty()
    1 * supplier.apply(CUSTOM_KIND) >> Optional.empty()
    result == KubernetesKindProperties.withDefaultProperties(CUSTOM_KIND)

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(CUSTOM_KIND) >> Optional.empty()
    1 * supplier.apply(CUSTOM_KIND) >> Optional.of(customProperties)
    result == customProperties

    when:
    result = kindRegistry.getKindProperties(CUSTOM_KIND)

    then:
    _ * globalRegistry.getKindProperties(CUSTOM_KIND) >> Optional.empty()
    0 * supplier.apply(CUSTOM_KIND) >> Optional.of(customProperties)
    result == customProperties
  }
}
