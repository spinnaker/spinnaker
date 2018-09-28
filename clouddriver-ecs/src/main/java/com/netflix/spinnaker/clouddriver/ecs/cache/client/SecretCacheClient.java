/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SECRETS;

@Component
public class SecretCacheClient extends AbstractCacheClient<Secret>{

  @Autowired
  public SecretCacheClient(Cache cacheView) {
    super(cacheView, SECRETS.toString());
  }

  @Override
  protected Secret convert(CacheData cacheData) {
    Secret secret = new Secret();
    Map<String, Object> attributes = cacheData.getAttributes();

    secret.setAccount((String) attributes.get("account"));
    secret.setRegion((String) attributes.get("region"));
    secret.setName((String) attributes.get("secretName"));
    secret.setArn((String) attributes.get("secretArn"));

    return secret;
  }
}
