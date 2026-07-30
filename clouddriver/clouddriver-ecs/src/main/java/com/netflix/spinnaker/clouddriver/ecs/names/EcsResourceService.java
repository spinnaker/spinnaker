/*
 * Copyright 2020 Expedia, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.names;

import java.util.List;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.Tag;

public class EcsResourceService implements EcsResource {

  private final Service service;

  public EcsResourceService(final Service service) {
    this.service = service;
  }

  @Override
  public String getName() {
    return service.serviceName();
  }

  @Override
  public void setName(String name) {
    // V2 SDK model objects are immutable; setName is a no-op.
    // This method exists to satisfy the EcsResource interface contract.
    throw new UnsupportedOperationException("Cannot set name on immutable v2 Service object");
  }

  @Override
  public List<Tag> getResourceTags() {
    return service.tags();
  }
}
