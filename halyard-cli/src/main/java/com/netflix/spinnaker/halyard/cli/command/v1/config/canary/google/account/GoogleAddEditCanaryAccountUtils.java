/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.google.account;

import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.CanaryUtils;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryServiceIntegration;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public class GoogleAddEditCanaryAccountUtils {

  static void updateSupportedTypes(Canary canary, GoogleCanaryAccount account) {
    GoogleCanaryServiceIntegration googleCanaryServiceIntegration =
        (GoogleCanaryServiceIntegration)
            CanaryUtils.getServiceIntegrationByClass(canary, GoogleCanaryServiceIntegration.class);

    if (googleCanaryServiceIntegration.isStackdriverEnabled()) {
      account
          .getSupportedTypes()
          .add(AbstractCanaryServiceIntegration.SupportedTypes.METRICS_STORE);
    } else {
      account
          .getSupportedTypes()
          .remove(AbstractCanaryServiceIntegration.SupportedTypes.METRICS_STORE);
    }

    if (googleCanaryServiceIntegration.isGcsEnabled()
        && StringUtils.isNotEmpty(account.getBucket())) {
      account
          .getSupportedTypes()
          .addAll(
              Arrays.asList(
                  AbstractCanaryServiceIntegration.SupportedTypes.CONFIGURATION_STORE,
                  AbstractCanaryServiceIntegration.SupportedTypes.OBJECT_STORE));
    } else {
      account
          .getSupportedTypes()
          .removeAll(
              Arrays.asList(
                  AbstractCanaryServiceIntegration.SupportedTypes.CONFIGURATION_STORE,
                  AbstractCanaryServiceIntegration.SupportedTypes.OBJECT_STORE));
    }
  }
}
