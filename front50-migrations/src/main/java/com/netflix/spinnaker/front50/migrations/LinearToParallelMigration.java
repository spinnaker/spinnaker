/*
 * Copyright 2016 Netflix, Inc.
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LinearToParallelMigration implements Migration {
  private static final Logger log = LoggerFactory.getLogger(LinearToParallelMigration.class);

  // Only valid until Novemeber 1st, 2016
  private static final Date VALID_UNTIL = new GregorianCalendar(2016, 10, 1).getTime();

  private Clock clock = Clock.systemDefaultZone();

  @Autowired PipelineDAO pipelineDAO;

  @Autowired PipelineStrategyDAO pipelineStrategyDAO;

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting Linear -> Parallel Migration");
    pipelineDAO.all().stream()
        .filter(pipeline -> !(Boolean.valueOf(pipeline.getOrDefault("parallel", false).toString())))
        .forEach(
            pipeline -> {
              migrate(pipelineDAO, "pipeline", pipeline);
            });

    pipelineStrategyDAO.all().stream()
        .filter(strategy -> !(Boolean.valueOf(strategy.getOrDefault("parallel", false).toString())))
        .forEach(
            strategy -> {
              migrate(pipelineStrategyDAO, "pipeline strategy", strategy);
            });
  }

  private void migrate(ItemDAO<Pipeline> dao, String type, Pipeline pipeline) {
    log.info(
        format(
            "Migrating {} '{}' from linear -> parallel",
            value("type", type),
            value("pipelineId", pipeline.getId())));

    AtomicInteger refId = new AtomicInteger(0);
    List<Map<String, Object>> stages =
        (List<Map<String, Object>>) pipeline.getOrDefault("stages", Collections.emptyList());
    stages.forEach(
        stage -> {
          stage.put("refId", String.valueOf(refId.get()));
          if (refId.get() > 0) {
            stage.put(
                "requisiteStageRefIds", Collections.singletonList(String.valueOf(refId.get() - 1)));
          } else {
            stage.put("requisiteStageRefIds", Collections.emptyList());
          }

          refId.incrementAndGet();
        });

    pipeline.put("parallel", true);
    dao.update(pipeline.getId(), pipeline);

    log.info(
        format(
            "Migrated %s '%s' from linear -> parallel",
            value("type", type), value("pipelineId", pipeline.getId())));
  }
}
