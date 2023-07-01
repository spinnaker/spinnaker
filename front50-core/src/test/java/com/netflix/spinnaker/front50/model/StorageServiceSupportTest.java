/*
 * Copyright 2023 Salesforce, Inc.
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
package com.netflix.spinnaker.front50.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import rx.Scheduler;

class StorageServiceSupportTest {

  /** Pipeline is an arbitrary choice of an object type. */
  class TestDAO extends StorageServiceSupport<Pipeline> {
    public TestDAO(
        StorageService service,
        Scheduler scheduler,
        ObjectKeyLoader objectKeyLoader,
        StorageServiceConfigurationProperties.PerObjectType configurationProperties,
        Registry registry,
        CircuitBreakerRegistry circuitBreakerRegistry) {
      super(
          ObjectType.PIPELINE,
          service,
          scheduler,
          objectKeyLoader,
          configurationProperties,
          registry,
          circuitBreakerRegistry);
    }
  }

  private StorageService storageService = mock(StorageService.class);

  private Scheduler scheduler = mock(Scheduler.class);

  private StorageServiceConfigurationProperties.PerObjectType testDAOConfigProperties =
      new StorageServiceConfigurationProperties.PerObjectType();

  private TestDAO testDAO =
      new TestDAO(
          storageService,
          scheduler,
          new DefaultObjectKeyLoader(storageService),
          testDAOConfigProperties,
          new NoopRegistry(),
          CircuitBreakerRegistry.ofDefaults());

  @Test
  void fetchAllItemsOptimizedWithNullId() {
    // If presented with an existing item with a null id, make sure
    // fetchAllItemsOptimized ignores it, as opposed to trying to build an
    // object key for a null id, which results in a NullPointerException.

    // No need for any items from the data store for this
    List<Pipeline> modifiedItems = Collections.emptyList();
    List<Pipeline> deletedItems = Collections.emptyList();
    Map<String, List<Pipeline>> newerItems =
        Map.of("not_deleted", modifiedItems, "deleted", deletedItems);
    doReturn(newerItems)
        .when(storageService)
        .loadObjectsNewerThan(eq(ObjectType.PIPELINE), anyLong());

    HashSet<Pipeline> existingItems = new HashSet<>();
    Pipeline itemWithNullId = new Pipeline();
    itemWithNullId.setName("pipeline1");
    itemWithNullId.setId(null);
    existingItems.add(itemWithNullId);

    Set<Pipeline> resultingItems = testDAO.fetchAllItemsOptimized(existingItems);
    assertThat(resultingItems).isNotNull();
    assertThat(resultingItems).isEmpty();

    verify(storageService).loadObjectsNewerThan(eq(ObjectType.PIPELINE), anyLong());
  }
}
