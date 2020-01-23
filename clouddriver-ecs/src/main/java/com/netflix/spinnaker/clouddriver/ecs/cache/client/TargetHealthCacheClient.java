/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TARGET_HEALTHS;

import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsTargetHealth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TargetHealthCacheClient extends AbstractCacheClient<EcsTargetHealth> {
  private ObjectMapper objectMapper;

  @Autowired
  public TargetHealthCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    super(cacheView, TARGET_HEALTHS.toString());
    this.objectMapper = objectMapper;
  }

  @Override
  protected EcsTargetHealth convert(CacheData cacheData) {
    EcsTargetHealth targetHealth = new EcsTargetHealth();
    Map<String, Object> attributes = cacheData.getAttributes();

    targetHealth.setTargetGroupArn((String) attributes.get("targetGroupArn"));

    if (attributes.containsKey("targetHealthDescriptions")) {
      List<Map<String, Object>> targetHealthDescriptions =
          (List<Map<String, Object>>) attributes.get("targetHealthDescriptions");
      List<TargetHealthDescription> deserializedTargetHealthDescriptions =
          new ArrayList<>(targetHealthDescriptions.size());

      for (Map<String, Object> serializedTargetHealthDescription : targetHealthDescriptions) {
        if (serializedTargetHealthDescription != null) {
          deserializedTargetHealthDescriptions.add(
              objectMapper.convertValue(
                  serializedTargetHealthDescription, TargetHealthDescription.class));
        }
      }

      targetHealth.setTargetHealthDescriptions(deserializedTargetHealthDescriptions);
    } else {
      targetHealth.setTargetHealthDescriptions(Collections.emptyList());
    }

    return targetHealth;
  }
}
