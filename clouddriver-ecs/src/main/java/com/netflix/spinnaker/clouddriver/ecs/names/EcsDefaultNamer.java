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

import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import org.springframework.stereotype.Component;

/*
 * The default naming strategy for ECS that just delegates to the NamerRegistry default
 */
@Component
public class EcsDefaultNamer implements NamingStrategy<EcsResource> {

  private final Namer<Object> namer;

  public EcsDefaultNamer() {
    this.namer = NamerRegistry.getDefaultNamer();
  }

  @Override
  public String getName() {
    return "default";
  }

  @Override
  public void applyMoniker(EcsResource resource, Moniker moniker) {
    namer.applyMoniker(resource, moniker);
  }

  @Override
  public Moniker deriveMoniker(EcsResource resource) {
    return namer.deriveMoniker(resource);
  }
}
