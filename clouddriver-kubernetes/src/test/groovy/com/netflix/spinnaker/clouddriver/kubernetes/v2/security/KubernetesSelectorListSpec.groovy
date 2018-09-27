/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesSelectorListSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())

  List<MatchExpression> matchExpressionsFromYaml(String input) {
    return objectMapper.convertValue(yaml.load(input), new TypeReference<List<MatchExpression>>() {})
  }

  Map<String, String> matchLabelsFromYaml(String input) {
    return objectMapper.convertValue(yaml.load(input), new TypeReference<Map<String, String>>() {})
  }

  @Unroll
  def "renders well-formed match expressions for #selectorQuery"() {
    when:
    def matchExpressions = matchExpressionsFromYaml(matchExpressionsYaml)
    def matchLabels = matchLabelsFromYaml(matchLabelsYaml)
    KubernetesSelectorList list = KubernetesSelectorList.fromMatchExpressions(matchExpressions)
    list.addSelectors(KubernetesSelectorList.fromMatchLabels(matchLabels))

    then:
    list.toString() == selectorQuery

    where:
    matchExpressionsYaml                                                                                  | matchLabelsYaml                     | selectorQuery
    "[{ key: tier, operator: In, values: [cache] }]"                                                      | "{}"                                | "tier in (cache)"
    "[]"                                                                                                  | "{load: balancer}"                  | "load = balancer"
    "[{ key: tier, operator: In, values: [cache] }]"                                                      | "{load: balancer}"                  | "tier in (cache),load = balancer"
    "[{ key: stack, operator: NotIn, values: [canary, backend] }]"                                        | "{}"                                | "stack notin (canary, backend)"
    "[{ key: stack, operator: NotIn, values: [canary, backend] }, { key: production, operator: Exists }]" | "{}"                                | "stack notin (canary, backend),production"
    "[]"                                                                                                  | "{load: balancer, balance: loader}" | "load = balancer,balance = loader"
    "[{ key: stack, operator: NotIn, values: [canary, backend] }, { key: production, operator: Exists }]" | "{load: balancer, balance: loader}" | "stack notin (canary, backend),production,load = balancer,balance = loader"
    "[{ key: production, operator: DoesNotExist }]"                                                       | "{}"                                | "!production"
  }
}
