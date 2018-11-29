/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.providers.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScope
import spock.lang.Specification
import spock.lang.Unroll

class StackdriverCanaryMetricSetQueryConfigSpec extends Specification {

  @Unroll
  void "Referenced template #customFilterTemplate expands properly"() {
    given:
    CanaryConfig canaryConfig = CanaryConfig.builder().templates(templates).build()
    StackdriverCanaryMetricSetQueryConfig stackdriverCanaryMetricSetQueryConfig =
      StackdriverCanaryMetricSetQueryConfig.builder().customFilterTemplate(customFilterTemplate).build()
    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope(extendedScopeParams: scopeParams)

    expect:
    QueryConfigUtils.expandCustomFilter(canaryConfig, stackdriverCanaryMetricSetQueryConfig, stackdriverCanaryScope) == expectedExpandedTemplate

    where:
    templates                                             | customFilterTemplate | scopeParams       || expectedExpandedTemplate
    ["my-template": 'A test: key1=${key1}.']              | "my-template"        | [key1: "value-1"] || "A test: key1=value-1."
    ["my-template-1": 'A test: key1=${key1}.',
     "my-template-2": 'A test: key2=${key2}.']            | "my-template-2"      | [key2: "value-2"] || "A test: key2=value-2."
    ["my-template-1": 'A test: key1=${key1}.',
     "my-template-2": 'A test: key2=${key2}.']            | "my-template-1"      | [key1: "value-1"] || "A test: key1=value-1."
    ["my-template": 'A test: key1=${key1} key2=${key2}.'] | "my-template"        | [key1: "value-1",
                                                                                    key2: "value-2"] || "A test: key1=value-1 key2=value-2."
    ["my-template": 'A test: key1=something1.']           | "my-template"        | null              || "A test: key1=something1."
    ["my-template": 'A test: key1=something1.']           | "my-template"        | [:]               || "A test: key1=something1."
  }

  @Unroll
  void "Custom filter takes precedence over custom filter template #customFilterTemplate"() {
    given:
    CanaryConfig canaryConfig = CanaryConfig.builder().templates(templates).build()
    StackdriverCanaryMetricSetQueryConfig stackdriverCanaryMetricSetQueryConfig =
      StackdriverCanaryMetricSetQueryConfig.builder().customFilterTemplate(customFilterTemplate).customInlineTemplate(customInlineTemplate).build()
    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope(extendedScopeParams: scopeParams)

    expect:
    QueryConfigUtils.expandCustomFilter(canaryConfig, stackdriverCanaryMetricSetQueryConfig, stackdriverCanaryScope) == expectedExpandedTemplate

    where:
    templates                                             | customFilterTemplate | customInlineTemplate           | scopeParams       || expectedExpandedTemplate
    ["my-template": 'A test: key1=${key1}.']              | "my-template"        | 'An inline template: ${key1}.' | [key1: "value-1"] || "An inline template: value-1."
    ["my-template-1": 'A test: key1=${key1}.',
     "my-template-2": 'A test: key2=${key2}.']            | "my-template-2"      | 'An inline template: ${key2}.' | [key2: "value-2"] || "An inline template: value-2."
    ["my-template-1": 'A test: key1=${key1}.',
     "my-template-2": 'A test: key2=${key2}.']            | "my-template-1"      | "An inline template."          | [key1: "value-1"] || "An inline template."
    ["my-template": 'A test: key1=${key1} key2=${key2}.'] | "my-template"        | "An inline template."          | [key1: "value-1",
                                                                                                                     key2: "value-2"] || "An inline template."
    ["my-template": 'A test: key1=something1.']           | "my-template"        | "An inline template."          | null              || "An inline template."
    ["my-template": 'A test: key1=something1.']           | "my-template"        | "An inline template."          | [:]               || "An inline template."
  }

  @Unroll
  void "Missing template, no templates, or missing variable all throw exceptions"() {
    given:
    CanaryConfig canaryConfig = CanaryConfig.builder().templates(templates).build()
    StackdriverCanaryMetricSetQueryConfig stackdriverCanaryMetricSetQueryConfig =
      StackdriverCanaryMetricSetQueryConfig.builder().customFilterTemplate(customFilterTemplate).build()
    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope(extendedScopeParams: scopeParams)

    when:
    QueryConfigUtils.expandCustomFilter(canaryConfig, stackdriverCanaryMetricSetQueryConfig, stackdriverCanaryScope)

    then:
    thrown IllegalArgumentException

    where:
    templates                                                 | customFilterTemplate | scopeParams
    ["my-template-1": 'A test: key1=${key1}.',
     "my-template-2": 'A test: key2=${key2}.']                | "my-template-x"        | null
    [:]                                                       | "my-template-x"        | null
    null                                                      | "my-template-x"        | null
    ["my-template": 'A test: key1=${key1} key2=${key2}.']     | "my-template"          | [key3: "value-3",
                                                                                          key4: "value-4"]
    ["my-template": 'A test: key1=$\\{key1} key2=$\\{key2}.'] | "my-template"          | [key3: "value-3",
                                                                                          key4: "value-4"]
    ["my-template": 'A test: key1=$\\{key1} key2=$\\{key2}.'] | 'my-template: ${key1}' | [key3: "value-3",
                                                                                          key4: "value-4"]
  }

  @Unroll
  void "Can use predefined variables in custom filter template"() {
    given:
    CanaryConfig canaryConfig = CanaryConfig.builder().templates(templates).build()
    StackdriverCanaryMetricSetQueryConfig stackdriverCanaryMetricSetQueryConfig =
      StackdriverCanaryMetricSetQueryConfig.builder().customFilterTemplate("my-template").build()
    StackdriverCanaryScope stackdriverCanaryScope =
      new StackdriverCanaryScope(project: "my-project", resourceType: "gce_instance", scope: "myapp-dev-v001", location: "us-east1", extendedScopeParams: [key1: "value-1"])

    expect:
    QueryConfigUtils.expandCustomFilter(canaryConfig, stackdriverCanaryMetricSetQueryConfig, stackdriverCanaryScope, (String[])["project", "resourceType", "scope", "location"]) == expectedExpandedTemplate

    where:
    templates                                                             || expectedExpandedTemplate
    ["my-template": 'A test: myGroupName=${scope} key1=${key1}.']         || "A test: myGroupName=myapp-dev-v001 key1=value-1."
    ["my-template": 'A test: project=${project} key1=${key1}.']           || "A test: project=my-project key1=value-1."
    ["my-template": 'A test: resourceType=${resourceType} key1=${key1}.'] || "A test: resourceType=gce_instance key1=value-1."
    ["my-template": 'A test: scope=${scope} key1=${key1}.']               || "A test: scope=myapp-dev-v001 key1=value-1."
    ["my-template": 'A test: region=${location} key1=${key1}.']           || "A test: region=us-east1 key1=value-1."
    ["my-template": 'A test: region=$\\{location} key1=$\\{key1}.']       || "A test: region=us-east1 key1=value-1."
  }
}
