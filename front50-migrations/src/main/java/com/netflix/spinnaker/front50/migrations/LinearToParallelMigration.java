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

import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

@Component
public class LinearToParallelMigration implements Migration {
  private static final Logger log = LoggerFactory.getLogger(LinearToParallelMigration.class);

  // Only valid until September 1st, 2016
  private static final Date VALID_UNTIL = new GregorianCalendar(2016, 8, 1).getTime();

  private Clock clock = Clock.systemDefaultZone();

  @Autowired
  PipelineDAO pipelineDAO;

  @Override
  public boolean isValid() {
    return  clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    pipelineDAO.all().stream()
        .filter(pipeline -> !((Boolean) pipeline.getOrDefault("parallel", false)))
        .forEach(pipeline -> {
          log.info(format("Migrating pipeline '%s' from linear -> parallel", pipeline.getId()));

          AtomicInteger refId = new AtomicInteger(0);
          List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.getOrDefault("stages", Collections.emptyList());
          stages.forEach(stage -> {
            stage.put("refId", String.valueOf(refId.get()));
            if (refId.get() > 0) {
              stage.put("requisiteStageRefIds", Collections.singletonList(String.valueOf(refId.get() - 1)));
            } else {
              stage.put("requisiteStageRefIds", Collections.emptyList());
            }

            refId.incrementAndGet();
          });

          pipeline.put("parallel", true);
          pipelineDAO.update(pipeline.getId(), pipeline);

          log.info(format("Migrated pipeline '%s' from linear -> parallel", pipeline.getId()));
        });
  }
}
