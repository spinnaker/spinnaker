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

import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V2PipelineTemplateIncludeToExcludeMigration implements Migration {

  // Only valid until April 1st, 2020
  private static final Date VALID_UNTIL = new GregorianCalendar(2020, 4, 1).getTime();
  // Set of string keys MPT inherit/exclude operate on.
  private static final List<String> INHERIT_KEYS =
      Arrays.asList("notifications", "parameters", "triggers");

  private final PipelineDAO pipelineDAO;

  private Clock clock = Clock.systemDefaultZone();

  @Autowired
  public V2PipelineTemplateIncludeToExcludeMigration(PipelineDAO pipelineDAO) {
    this.pipelineDAO = pipelineDAO;
  }

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting v2 pipeline template inherit to exclude migration");

    Predicate<Pipeline> hasInheritOnly =
        p -> {
          Map<String, Object> template = (Map<String, Object>) p.get("template");
          if (template == null) {
            return false;
          }
          String schema = (String) p.getOrDefault("schema", "");
          List<String> inherit = (List<String>) p.get("inherit");
          List<String> exclude = (List<String>) p.get("exclude");

          // There's 4 cases based on the existence of 'inherit' and 'exclude':
          // Neither exist -> do nothing.
          // Both exist -> Orca will honor exclude, do nothing.
          // Only exclude exists -> Orca will honor, do nothing.
          // Only inherit exists -> Need to migrate and set the complement as exclude.
          return schema.equals("v2") && inherit != null && exclude == null;
        };

    pipelineDAO.all().stream()
        .filter(hasInheritOnly)
        .forEach(pipeline -> migrate(pipelineDAO, pipeline));
  }

  private void migrate(ItemDAO<Pipeline> dao, Pipeline pipeline) {
    List<String> inherit = (List<String>) pipeline.get("inherit");
    if (inherit == null) {
      return;
    }

    List<String> exclude = new ArrayList<>(INHERIT_KEYS);
    exclude.removeAll(inherit);

    pipeline.put("exclude", exclude);
    pipeline.remove("inherit");
    dao.update(pipeline.getId(), pipeline);

    log.info(
        "Added pipeline template exclude (application: {}, pipelineId: {}, exclude: {}) from (inherit: {})",
        pipeline.getApplication(),
        pipeline.getId(),
        exclude,
        inherit);
  }
}
