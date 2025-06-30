/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.front50.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.Front50SqlProperties;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader;
import com.netflix.spinnaker.front50.model.SqlStorageService;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO;
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Clock;
import java.util.concurrent.Executors;

public class SqlPipelineDAOTestConfiguration {

  public static DefaultPipelineDAO createPipelineDAO(SqlTestUtil.TestDatabase database) {
    Scheduler scheduler = Schedulers.from(Executors.newFixedThreadPool(1));

    StorageServiceConfigurationProperties.PerObjectType pipelineDAOConfigProperties =
        new StorageServiceConfigurationProperties().getPipeline();

    SqlStorageService storageService =
        new SqlStorageService(
            new ObjectMapper(),
            new NoopRegistry(),
            database.context,
            Clock.systemDefaultZone(),
            new SqlRetryProperties(),
            1,
            "default",
            new Front50SqlProperties());

    // Configure PipelineDAO properties
    pipelineDAOConfigProperties.setRefreshMs(0);
    pipelineDAOConfigProperties.setShouldWarmCache(false);

    DefaultPipelineDAO pipelineDAO =
        new DefaultPipelineDAO(
            storageService,
            scheduler,
            new DefaultObjectKeyLoader(storageService),
            pipelineDAOConfigProperties,
            new NoopRegistry(),
            CircuitBreakerRegistry.ofDefaults());

    // refreshing to initialize the cache with an empty set
    pipelineDAO.all(true);

    return pipelineDAO;
  }
}
