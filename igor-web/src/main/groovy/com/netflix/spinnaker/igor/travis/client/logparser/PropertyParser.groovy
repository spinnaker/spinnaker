/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.travis.client.logparser

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class PropertyParser {
    static final String MAGIC_SEARCH_STRING = "SPINNAKER_PROPERTY_"
    static Map<String, Object> extractPropertiesFromLog(String buildLog) {
        Map<String, Object> map = new HashMap<String, Object>()
        buildLog.split('\n').each { String line ->
            if (line =~ MAGIC_SEARCH_STRING) {
                log.debug "Identified: ${line}"
                def splittedLine = line.split('=')
                String key = splittedLine[0].replaceFirst(MAGIC_SEARCH_STRING, "").toLowerCase()
                String value = splittedLine[1].trim()
                log.info "${key}:${value}"
                map.put(key,value)
            }
        }
        map
    }
}
