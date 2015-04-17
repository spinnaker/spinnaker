/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.util

import org.apache.commons.lang.text.StrSubstitutor

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 * @author clin
 */
class ContextParameterProcessor {

  static Map process(Map parameters, Map context) {
    if(!parameters){
      return null
    }

    Map flattenedMap = flattenMap('', context)
    StrSubstitutor substitutor = new StrSubstitutor(flattenedMap)
    parameters.collectEntries { k, v ->
      [k, substitutor.replace(v)]
    }
  }

  static Map flattenMap(String prefix, Object o) {
    Map result = [:]
    if (o instanceof Map) {
      o.each { k, v ->
        result = result + flattenMap("${prefix.empty ? '' : (prefix + '.')}${k}", v)
      }
    } else {
      result[prefix] = o
    }
    result
  }

}
