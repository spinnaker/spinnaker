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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.converter;

import com.netflix.spinnaker.kork.yaml.YamlHelper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

// Who needs type-checking anyway?
@SuppressWarnings("unchecked")
public class PipelineTemplateConverter {

  private static final Logger log = LoggerFactory.getLogger(PipelineTemplateConverter.class);

  public String convertToPipelineTemplate(Map<String, Object> pipeline) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("schema", "1");
    p.put(
        "id",
        String.format(
            "%s-%s",
            pipeline.getOrDefault("application", "spinnaker"),
            ((String) pipeline.getOrDefault("name", "generatedTemplate")).replaceAll("\\W", "")));
    p.put("metadata", generateMetadata(pipeline));
    p.put("protect", false);
    p.put("configuration", generateConfiguration(pipeline));
    p.put("variables", new ArrayList<>());
    p.put("stages", convertStages((List) pipeline.get("stages")));

    DumperOptions options = new DumperOptions();
    options.setIndent(2);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setSplitLines(false);
    Yaml yaml = YamlHelper.newYamlDumperOptions(options);

    String output = yaml.dump(p);

    return String.format("%s%s", loadTemplateHeader(), output);
  }

  private Map<String, Object> generateMetadata(Map<String, Object> pipeline) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", pipeline.getOrDefault("name", "GIVE ME A NAME"));
    m.put("description", pipeline.getOrDefault("description", "GIVE ME A DESCRIPTION"));
    m.put("owner", pipeline.get("lastModifiedBy"));
    m.put(
        "scopes",
        (pipeline.get("application") == null)
            ? new ArrayList<>()
            : Collections.singletonList(pipeline.get("application")));
    return m;
  }

  private Map<String, Object> generateConfiguration(Map<String, Object> pipeline) {
    Map<String, Object> m = new LinkedHashMap<>();
    Map<String, Object> cm = new LinkedHashMap<>();
    cm.put("limitConcurrent", true);
    cm.put("maxConcurrentExecutions", 0);
    m.put("concurrentExecutions", cm);
    m.put("triggers", convertTriggers((List) pipeline.get("triggers")));
    m.put("parameters", pipeline.getOrDefault("parameterConfig", new ArrayList<>()));
    m.put("notifications", convertNotifications((List) pipeline.get("notifications")));
    m.put("expectedArtifacts", pipeline.get("expectedArtifacts"));
    return m;
  }

  private List<Map<String, Object>> convertStages(List<Map<String, Object>> stages) {
    return stages.stream()
        .map(
            s -> {
              List<String> dependsOn = new ArrayList<>();
              if (s.containsKey("requisiteStageRefIds")
                  && !((List) s.get("requisiteStageRefIds")).isEmpty()) {
                dependsOn = buildStageRefIds(stages, (List) s.get("requisiteStageRefIds"));
              }

              Map<String, Object> stage = new LinkedHashMap<>();
              stage.put("id", getStageId((String) s.get("type"), (String) s.get("refId")));
              stage.put("type", s.get("type"));
              stage.put("dependsOn", dependsOn);
              stage.put("name", s.get("name"));
              stage.put("config", scrubStageConfig(s));
              return stage;
            })
        .collect(Collectors.toList());
  }

  private static Map<String, Object> scrubStageConfig(Map<String, Object> config) {
    Map<String, Object> working = new LinkedHashMap<>(config);
    working.remove("type");
    working.remove("name");
    working.remove("refId");
    working.remove("requisiteStageRefIds");
    return working;
  }

  private static List<String> buildStageRefIds(
      List<Map<String, Object>> stages, List<String> requisiteStageRefIds) {
    List<String> refIds = new ArrayList<>();
    for (String refId : requisiteStageRefIds) {
      Optional<String> stage =
          stages.stream()
              .filter(s -> refId.equals(s.get("refId")))
              .map(s -> getStageId((String) s.get("type"), (String) s.get("refId")))
              .findFirst();
      stage.ifPresent(refIds::add);
    }
    return refIds;
  }

  private static String getStageId(String type, String refId) {
    return String.format("%s%s", type, refId);
  }

  private List<Map<String, Object>> convertTriggers(List<Map<String, Object>> triggers) {
    if (triggers == null) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> ret = new ArrayList<>(triggers.size());

    int i = 0;
    for (Map<String, Object> trigger : triggers) {
      trigger.put("name", String.format("unnamed%d", i));
      i++;
      ret.add(trigger);
    }

    return ret;
  }

  private List<Map<String, Object>> convertNotifications(List<Map<String, Object>> notifications) {
    if (notifications == null) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> ret = new ArrayList<>(notifications.size());

    int i = 0;
    for (Map<String, Object> notification : notifications) {
      notification.put("name", String.format("%s%d", notification.get("type"), i));
      i++;
      ret.add(notification);
    }

    return ret;
  }

  private String loadTemplateHeader() {
    InputStream is = getClass().getResourceAsStream("/pipelineTemplateHeader.txt");
    if (is == null) {
      log.error("Could not load pipeline template header resource");
      return "# GENERATED BY spinnaker\n";
    }
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }
}
