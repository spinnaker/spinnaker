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

package com.netflix.spinnaker.clouddriver.elasticsearch.events;

import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchEntityTagger;
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEventHandler;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateServerGroupEventHandler implements OperationEventHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ElasticSearchEntityTagger serverGroupTagger;

  @Autowired
  public CreateServerGroupEventHandler(ElasticSearchEntityTagger serverGroupTagger) {
    this.serverGroupTagger = serverGroupTagger;
  }

  @Override
  public void handle(OperationEvent operationEvent) {
    if (!(operationEvent instanceof CreateServerGroupEvent)) {
      return;
    }

    log.debug("Handling create server group event ({})", operationEvent);

    CreateServerGroupEvent createServerGroupEvent = (CreateServerGroupEvent) operationEvent;
    serverGroupTagger.deleteAll(
        createServerGroupEvent.getCloudProvider(),
        createServerGroupEvent.getAccountId(),
        createServerGroupEvent.getRegion(),
        EntityTagger.ENTITY_TYPE_SERVER_GROUP,
        createServerGroupEvent.getName());
  }
}
