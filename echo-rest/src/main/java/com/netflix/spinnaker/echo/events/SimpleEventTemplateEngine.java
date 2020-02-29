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

package com.netflix.spinnaker.echo.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimpleEventTemplateEngine implements RestEventTemplateEngine {

  private static final Logger log = LoggerFactory.getLogger(SimpleEventTemplateEngine.class);

  private ObjectMapper objectMapper = EchoObjectMapper.getInstance();

  public Map render(String templateString, Map eventMap) {
    Map renderedResultMap = null;
    try {
      String renderedResult =
          templateString.replace("{{event}}", objectMapper.writeValueAsString(eventMap));
      renderedResultMap = objectMapper.readValue(renderedResult, Map.class);
    } catch (Exception e) {
      log.error("Unable to render result.", e);
    }
    return renderedResultMap;
  }
}
