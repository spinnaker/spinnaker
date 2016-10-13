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

package com.netflix.spinnaker.halyard.model.v1.providers.dockerRegistry;

import com.netflix.spinnaker.halyard.model.v1.providers.Account;
import com.netflix.spinnaker.halyard.validate.v1.ValidateField;
import com.netflix.spinnaker.halyard.validate.v1.ValidateNotNull;
import com.netflix.spinnaker.halyard.validate.v1.providers.ValidateFileExists;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class DockerRegistryAccount extends Account {
  @ValidateField(validators = {ValidateNotNull.class})
  private String address;
  private String username;
  private String password;
  @ValidateField(validators = {ValidateFileExists.class})
  private String passwordFile;
  private String email;
  private List<String> repositories = new ArrayList<>();
}
