/*
 * Copyright 2017 Google, Inc.
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

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import java.time.Clock;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TriggerConstraintMigration implements Migration {
  private static final Logger log = LoggerFactory.getLogger(TriggerConstraintMigration.class);

  // Only valid until March 1st, 2018
  private static final Date VALID_UNTIL = new GregorianCalendar(2018, 3, 1).getTime();

  private Clock clock = Clock.systemDefaultZone();

  @Autowired PipelineDAO pipelineDAO;

  @Autowired PipelineStrategyDAO pipelineStrategyDAO;

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting trigger constraint migration");
    Predicate<Pipeline> hasTriggerConstraints =
        p -> {
          Map trigger = (Map) p.get("trigger");
          return trigger != null && !trigger.isEmpty() && trigger.containsKey("constraints");
        };
    pipelineDAO.all().stream()
        .filter(hasTriggerConstraints)
        .forEach(pipeline -> migrate(pipelineDAO, "pipeline", pipeline));

    pipelineStrategyDAO.all().stream()
        .filter(hasTriggerConstraints)
        .forEach(strategy -> migrate(pipelineStrategyDAO, "pipeline strategy", strategy));
  }

  private void migrate(ItemDAO<Pipeline> dao, String type, Pipeline pipeline) {
    log.info(
        format(
            "Migrating {} '{}' trigger constraints",
            value("type", type),
            value("pipelineId", pipeline.getId())));

    pipeline.put("payloadConstraints", pipeline.get("constraints"));
    dao.update(pipeline.getId(), pipeline);

    log.info(
        format(
            "Migrated %s '%s' trigger constraints",
            value("type", type), value("pipelineId", pipeline.getId())));
  }
}
