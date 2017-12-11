/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.composer.ComposerException;
import org.yaml.snakeyaml.parser.ParserException;

import java.util.Arrays;
import java.util.List;

public class YamlRenderedValueConverter implements RenderedValueConverter {

  private static final List<String> YAML_KEYWORDS = Arrays.asList("yes", "no", "on", "off");

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Yaml yaml;

  public YamlRenderedValueConverter(Yaml yaml) {
    this.yaml = yaml;
  }

  @Override
  public Object convertRenderedValue(String renderedValue) {
    if (containsEL(renderedValue) || isYamlKeyword(renderedValue) || containsYamlParsingExceptions(renderedValue)) {
      return renderedValue;
    }
    if (containsNoExpandMarker(renderedValue)) {
      return trimNoExpandMarker(renderedValue);
    }

    try {
      Object converted = yaml.load(renderedValue);
      if (converted == null || converted instanceof String) {
        return "".equals(converted) || "".equals(renderedValue) ? null : renderedValue;
      }
      return converted;
    } catch (ComposerException ce) {
      throw new TemplateRenderException("template produced invalid yaml", ce);
    } catch (ParserException pe) {
      if (pe.getProblem().contains("expected '<document start>'")) {
        log.info("YAML parser expected start of document, assuming rendered value is desired state");
        return renderedValue;
      }
      throw pe;
    }
  }

  private static boolean containsEL(String renderedValue) {
    return renderedValue.trim().startsWith("${");
  }

  private static boolean isYamlKeyword(String renderedValue) {
    return YAML_KEYWORDS.contains(renderedValue.toLowerCase());
  }

  private static boolean containsYamlParsingExceptions(String renderedValue) {
    return renderedValue != null &&
      renderedValue.startsWith("*"); // A markdown list or bold: YAML will parse this as an alias and fail.
  }

  private static boolean containsNoExpandMarker(String renderedValue) {
    return renderedValue.startsWith("noexpand:");
  }

  private static String trimNoExpandMarker(String renderedValue) {
    return renderedValue.substring("noexpand:".length(), renderedValue.length());
  }
}
