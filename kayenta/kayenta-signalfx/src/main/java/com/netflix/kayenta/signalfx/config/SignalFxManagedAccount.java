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

package com.netflix.kayenta.signalfx.config;

import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class SignalFxManagedAccount {

  @NotNull private String name;

  private String accessToken;

  private List<AccountCredentials.Type> supportedTypes =
      Collections.singletonList(AccountCredentials.Type.METRICS_STORE);

  @Nullable private RemoteService endpoint;

  @Nullable private String defaultScopeKey;

  @Nullable private String defaultLocationKey;
}
