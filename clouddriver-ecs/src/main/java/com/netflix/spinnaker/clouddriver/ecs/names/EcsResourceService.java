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

import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Tag;
import java.util.List;

public class EcsResourceService implements EcsResource {

  private final Service service;

  public EcsResourceService(final Service service) {
    this.service = service;
  }

  @Override
  public String getName() {
    return service.getServiceName();
  }

  @Override
  public void setName(String name) {
    service.setServiceName(name);
  }

  @Override
  public List<Tag> getResourceTags() {
    return service.getTags();
  }
}
