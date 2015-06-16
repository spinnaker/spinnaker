/*
 * Copyright 2014 Netflix, Inc.
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
 */


package com.netflix.spinnaker.gate.config

import groovy.text.GStringTemplateEngine
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
@ConfigurationProperties("insights")
class InsightConfiguration {
  List<Link> serverGroup = []
  List<Link> instance = []

  static class Link {
    private static final GStringTemplateEngine templateEngine = new GStringTemplateEngine()

    String url
    String label

    Link applyContext(Map<String, String> context) {
      try {
        def url = templateEngine.createTemplate(url).make(context.withDefault { null }).toString()
        return new Link(
          url: url,
          label: label
        )
      } catch (Exception e) {
        log.info("Unable to apply context to link (url: ${url})", e)
        return this
      }
    }
  }
}

