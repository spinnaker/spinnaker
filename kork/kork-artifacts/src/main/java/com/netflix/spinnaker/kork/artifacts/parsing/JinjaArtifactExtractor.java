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

package com.netflix.spinnaker.kork.artifacts.parsing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Translates String messages into Spinnaker artifacts using a supplied Jinja template */
@Slf4j
public class JinjaArtifactExtractor implements ArtifactExtractor {
  private final JinjavaFactory jinjavaFactory;
  private final ObjectMapper objectMapper;
  private final String jinjaTemplate;

  private static final TypeReference<List<Artifact>> artifactListReference =
      new TypeReference<List<Artifact>>() {};
  private static final TypeReference<Map<String, ?>> stringMapReference =
      new TypeReference<Map<String, ?>>() {};

  private JinjaArtifactExtractor(
      String jinjaTemplate, JinjavaFactory jinjavaFactory, ObjectMapper objectMapper) {
    this.jinjaTemplate = jinjaTemplate;
    this.jinjavaFactory = jinjavaFactory;
    this.objectMapper = objectMapper;
  }

  public List<Artifact> getArtifacts(String messagePayload) {
    if (StringUtils.isEmpty(messagePayload)) {
      return Collections.emptyList();
    }
    return readArtifactList(jinjaTransform(messagePayload));
  }

  private String jinjaTransform(String messagePayload) {
    if (StringUtils.isEmpty(jinjaTemplate)) {
      return messagePayload;
    }
    Jinjava jinja = jinjavaFactory.create();
    Map<String, ?> context = readMapValue(messagePayload);
    return jinja.render(jinjaTemplate, context);
  }

  private Map<String, ?> readMapValue(String messagePayload) {
    try {
      return objectMapper.readValue(messagePayload, stringMapReference);
    } catch (IOException ioe) {
      log.error(messagePayload);
      throw new RuntimeException(ioe);
    }
  }

  private List<Artifact> readArtifactList(String hydratedTemplate) {
    try {
      return objectMapper.readValue(hydratedTemplate, artifactListReference);
    } catch (IOException ioe) {
      // Failure to parse artifacts from the message indicates either
      // the message payload does not match the provided template or
      // there is no template and no artifacts are expected
      log.warn("Unable to parse artifact from {}", hydratedTemplate, ioe);
    }
    return Collections.emptyList();
  }

  @RequiredArgsConstructor
  public static class Factory {
    private final JinjavaFactory jinjavaFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JinjaArtifactExtractor create(InputStream templateStream) {
      String template = readTemplateStream(templateStream);
      return this.create(template);
    }

    public JinjaArtifactExtractor create(String template) {
      return new JinjaArtifactExtractor(template, jinjavaFactory, objectMapper);
    }

    private String readTemplateStream(InputStream templateStream) {
      if (templateStream == null) {
        return "";
      } else {

        try (InputStreamReader isr =
                new InputStreamReader(
                    new BufferedInputStream(templateStream), Charset.forName("UTF-8"));
            StringWriter sw = new StringWriter()) {
          int charsRead;
          final char[] buf = new char[4096];
          while ((charsRead = isr.read(buf)) != -1) {
            sw.write(buf, 0, charsRead);
          }
          return sw.toString();
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
      }
    }
  }
}
