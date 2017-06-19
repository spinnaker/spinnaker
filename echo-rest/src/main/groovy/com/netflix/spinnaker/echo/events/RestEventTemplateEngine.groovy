/*
 * Copyright 2017 Armory, Inc.
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

package com.netflix.spinnaker.echo.events

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.ObjectMapper

interface RestEventTemplateEngine {
    Map render(String template, Map eventMap)
}


@Component
@Slf4j
class SimpleEventTemplateEngine implements RestEventTemplateEngine {

    ObjectMapper objectMapper = new ObjectMapper()

    Map render(String templateString, Map eventMap) {
        String renderedResult = templateString.replace('{{event}}', objectMapper.writeValueAsString(eventMap))
        return objectMapper.readValue(renderedResult, Map)
    }
}
