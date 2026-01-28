/*
 * Copyright (c) 2018 Nike, inc.
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
 */

package com.netflix.kayenta.signalfx.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.signalfx.service.SignalFxSignalFlowRemoteService;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class SignalFxNamedAccountCredentials extends AccountCredentials<SignalFxCredentials> {

  @NotNull private String name;

  @NotNull private SignalFxCredentials credentials;

  @NotNull private RemoteService endpoint;

  @Override
  public String getType() {
    return "signalfx";
  }

  @JsonIgnore SignalFxSignalFlowRemoteService signalFlowService;
}
