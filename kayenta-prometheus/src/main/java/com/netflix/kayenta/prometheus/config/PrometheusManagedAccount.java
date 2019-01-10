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
 */

package com.netflix.kayenta.prometheus.config;

import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class PrometheusManagedAccount {

  @NotNull
  private String name;

  // Location of prometheus server.
  @NotNull
  @Getter
  @Setter
  private RemoteService endpoint;

  // Optional parameter for use when protecting prometheus with basic auth.
  private String username;

  // Optional parameter for use when protecting prometheus with basic auth.
  private String password;

  // Optional parameter for use when protecting prometheus with basic auth.
  private String usernamePasswordFile;

  private List<AccountCredentials.Type> supportedTypes;
}
