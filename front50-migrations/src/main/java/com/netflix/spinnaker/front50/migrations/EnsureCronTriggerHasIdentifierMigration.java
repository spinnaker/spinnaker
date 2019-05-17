/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import java.time.Clock;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EnsureCronTriggerHasIdentifierMigration implements Migration {
  private static final Logger log =
      LoggerFactory.getLogger(EnsureCronTriggerHasIdentifierMigration.class);

  // Only valid until October 1st, 2018
  private static final Date VALID_UNTIL = new GregorianCalendar(2018, 10, 1).getTime();

  private final PipelineDAO pipelineDAO;

  private Clock clock = Clock.systemDefaultZone();

  @Autowired
  public EnsureCronTriggerHasIdentifierMigration(PipelineDAO pipelineDAO) {
    this.pipelineDAO = pipelineDAO;
  }

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting cron trigger identifier migration");
    Predicate<Pipeline> hasCronTrigger =
        p -> {
          List<Map> triggers = (List<Map>) p.get("triggers");
          return triggers != null
              && triggers.stream()
                  .anyMatch(
                      t -> {
                        String type = (String) t.get("type");
                        String id = (String) t.get("id");

                        return ("cron".equalsIgnoreCase(type) && (id == null || id.isEmpty()));
                      });
        };

    pipelineDAO.all().stream()
        .filter(hasCronTrigger)
        .forEach(pipeline -> migrate(pipelineDAO, pipeline));
  }

  private void migrate(ItemDAO<Pipeline> dao, Pipeline pipeline) {
    log.info(
        "Added cron trigger identifier (application: {}, pipelineId: {}, triggers: {})",
        pipeline.getApplication(),
        pipeline.getId(),
        pipeline.get("triggers"));

    List<Map<String, Object>> triggers = (List<Map<String, Object>>) pipeline.get("triggers");
    for (Map<String, Object> trigger : triggers) {
      String type = (String) trigger.get("type");
      String id = (String) trigger.get("id");

      if ("cron".equalsIgnoreCase(type) && (id == null || id.isEmpty())) {
        trigger.put("id", UUID.randomUUID().toString());
      }
    }
    dao.update(pipeline.getId(), pipeline);

    log.info(
        "Added cron trigger identifier (application: {}, pipelineId: {}, triggers: {})",
        pipeline.getApplication(),
        pipeline.getId(),
        pipeline.get("triggers"));
  }
}
