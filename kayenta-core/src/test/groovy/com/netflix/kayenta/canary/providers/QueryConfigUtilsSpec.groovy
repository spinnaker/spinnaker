/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.kayenta.canary.providers

import com.netflix.kayenta.canary.CanaryConfig
import spock.lang.Specification
import spock.lang.Unroll

class QueryConfigUtilsSpec extends Specification {

  @Unroll
  void "Templates in #templates are escaped to protect them from premature expression evaluation by orca"() {
    given:
    CanaryConfig canaryConfig = CanaryConfig.builder().templates(templates).build()

    expect:
    QueryConfigUtils.escapeTemplates(canaryConfig).getTemplates() == expectedEscapedTemplates

    where:
    templates                                             || expectedEscapedTemplates
    ['my-template': 'A test: key1=${key1}.']              || ['my-template': 'A test: key1=$\\{key1}.']
    ['my-template-1': 'A test: key1=${key1}.',
     'my-template-2': 'A test: key2=${key2}.']            || ['my-template-1': 'A test: key1=$\\{key1}.',
                                                              'my-template-2': 'A test: key2=$\\{key2}.']
    ['my-template': 'A test: key1=${key1} key2=${key2}.'] || ['my-template': 'A test: key1=$\\{key1} key2=$\\{key2}.']
  }

  @Unroll
  void "Template #template is unescaped so it can be expanded by freemarker"() {
    expect:
    QueryConfigUtils.unescapeTemplate(template) == expectedUnescapedTemplate

    where:
    template                                 || expectedUnescapedTemplate
    'A test: key1=$\\{key1}.'                || 'A test: key1=${key1}.'
    'A test: key1=$\\{key1} key2=$\\{key2}.' || 'A test: key1=${key1} key2=${key2}.'
    'A test: key1=${key1} key2=${key2}.'     || 'A test: key1=${key1} key2=${key2}.'
    'A test: key1=${key1} key2=$\\{key2}.'   || 'A test: key1=${key1} key2=${key2}.'
    'A test: key1=key1.'                     || 'A test: key1=key1.'
  }
}
