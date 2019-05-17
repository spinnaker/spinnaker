/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration.TemplateSource;
import java.time.Clock;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V2PipelineTemplateSourceToArtifactMigration implements Migration {

  // Only valid until April 1st, 2020
  private static final Date VALID_UNTIL = new GregorianCalendar(2020, 4, 1).getTime();

  private final PipelineDAO pipelineDAO;
  private final ObjectMapper objectMapper;

  private Clock clock = Clock.systemDefaultZone();

  @Autowired
  public V2PipelineTemplateSourceToArtifactMigration(
      PipelineDAO pipelineDAO, ObjectMapper objectMapper) {
    this.pipelineDAO = pipelineDAO;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting v2 pipeline template source to artifact migration");

    Predicate<Pipeline> hasV2TemplateSource =
        p -> {
          Map<String, Object> template = (Map<String, Object>) p.get("template");
          if (template == null) {
            return false;
          }
          String schema = (String) p.getOrDefault("schema", "");

          return schema.equals("v2")
              && isTemplateSource(template)
              && StringUtils.isNotEmpty((String) template.getOrDefault("source", ""));
        };

    pipelineDAO.all().stream()
        .filter(hasV2TemplateSource)
        .forEach(pipeline -> migrate(pipelineDAO, pipeline));
  }

  private boolean isTemplateSource(Object template) {
    try {
      TemplateSource _ignored = objectMapper.convertValue(template, TemplateSource.class);
    } catch (Exception e) {
      log.debug("Caught exception while deserializing TemplateSource", e);
      return false;
    }
    return true;
  }

  private void migrate(ItemDAO<Pipeline> dao, Pipeline pipeline) {
    Map<String, Object> templateArtifact = new HashMap<>();
    Map<String, Object> template = (Map<String, Object>) pipeline.get("template");
    String templateSource = (String) template.get("source");
    if (!templateSource.startsWith(TemplateSource.SPINNAKER_PREFIX)) {
      return;
    }

    templateArtifact.put(
        "artifactAccount", "front50ArtifactCredentials"); // Static creds for Front50 Artifacts.
    templateArtifact.put("type", "front50/pipelineTemplate");
    templateArtifact.put("reference", templateSource);

    pipeline.put("template", templateArtifact);
    dao.update(pipeline.getId(), pipeline);

    log.info(
        "Added pipeline template artifact (application: {}, pipelineId: {}, templateArtifact: {})",
        pipeline.getApplication(),
        pipeline.getId(),
        templateArtifact);
  }
}
