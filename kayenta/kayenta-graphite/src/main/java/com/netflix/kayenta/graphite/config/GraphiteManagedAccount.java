/*
 * Copyright 2018 Snap Inc.
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

package com.netflix.kayenta.graphite.config;

import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class GraphiteManagedAccount {
  @NotNull private String name;

  @NotNull private RemoteService endpoint;

  private List<AccountCredentials.Type> supportedTypes;
}
