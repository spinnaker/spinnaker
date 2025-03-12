/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.security.IAP;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class IAPConfig {

  boolean enabled;

  String jwtHeader;
  String issuerId;
  String audience;
  String iapVerifyKeyUrl;

  public IAPConfig(Security security) {
    if (!security.getAuthn().getIap().isEnabled()) {
      return;
    }

    IAP iap = security.getAuthn().getIap();

    this.enabled = iap.isEnabled();
    if (StringUtils.isNotEmpty(iap.getAudience())) {
      this.audience = iap.getAudience();
    }
    if (StringUtils.isNotEmpty(iap.getJwtHeader())) {
      this.jwtHeader = iap.getJwtHeader();
    }
    if (StringUtils.isNotEmpty(iap.getIssuerId())) {
      this.issuerId = iap.getIssuerId();
    }
    if (StringUtils.isNotEmpty(iap.getIapVerifyKeyUrl())) {
      this.iapVerifyKeyUrl = iap.getIapVerifyKeyUrl();
    }
  }
}
