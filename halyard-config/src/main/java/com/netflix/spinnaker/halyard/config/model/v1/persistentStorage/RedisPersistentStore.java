/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.persistentStorage;

import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import java.net.URI;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class RedisPersistentStore extends PersistentStore {
  // TODO(lwander): Make these configurable later (right now they are loaded from RuntimeSettings).
  String host;
  Integer port;

  @Override
  public PersistentStoreType persistentStoreType() {
    return PersistentStoreType.REDIS;
  }

  @Override
  public void setConnectionInfo(URI redisUri) {
    if (host == null || port == null) {
      host = redisUri.getHost();
      port = redisUri.getPort();
    }
  }
}
