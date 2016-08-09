/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.providers.BaseProvider;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResourceProviderHealthIndicator extends AbstractHealthIndicator {

  @Autowired
  @Setter
  List<BaseProvider> providers;

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    boolean isDown = false;
    for (BaseProvider provider : providers) {
      builder.withDetail(provider.getClass().getSimpleName(), provider.getHealthView());
      isDown = isDown || !provider.isProviderHealthy();
    }

    if (isDown) {
      builder.down();
    } else {
      builder.up();
    }
  }
}
