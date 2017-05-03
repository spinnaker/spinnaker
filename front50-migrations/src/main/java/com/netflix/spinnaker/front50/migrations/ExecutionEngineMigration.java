/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ExecutionEngineMigration implements Migration {

  private final PipelineDAO pipelineDAO;

  @Autowired
  public ExecutionEngineMigration(PipelineDAO pipelineDAO) {this.pipelineDAO = pipelineDAO;}

  @Override public boolean isValid() {
    return true;
  }

  @Override public void run() {
    log.info("Starting Linear -> Parallel Migration");
    Observable
      .from(pipelineDAO.all())
      .filter(this::isV1)
      .subscribe(this::migrate, this::onError);
  }

  private boolean isV1(Pipeline pipeline) {
    Object engine = pipeline.get("executionEngine");
    return engine == null || "v1".equals(engine);
  }

  private void migrate(Pipeline pipeline) {
    log.info("Migrating {} pipeline '{}' from v1 -> v2", pipeline.getApplication(), pipeline.getName());

    pipeline.put("executionEngine", "v2");
    pipelineDAO.update(pipeline.getId(), pipeline);

    log.info("Migrated {} pipeline '{}' from v1 -> v2", pipeline.getApplication(), pipeline.getName());
  }

  private void onError(Throwable throwable) {
    log.error("Error migrating pipeline to v2", throwable);
  }

  private static final Logger log = getLogger(ExecutionEngineMigration.class);
}
