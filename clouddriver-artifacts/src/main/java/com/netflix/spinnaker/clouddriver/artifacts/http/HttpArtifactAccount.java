/*
 * Copyright 2018 Joel Wilsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.http;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class HttpArtifactAccount implements ArtifactAccount {
  private String name;
  /*
    One of the following are required for auth:
     - username and password
     - usernamePasswordFile : path to file containing "username:password"
   */
  private String username;
  private String password;
  private String usernamePasswordFile;

  @JsonIgnore
  public boolean usesAuth() {
    return !(StringUtils.isEmpty(username) && StringUtils.isEmpty(password) && StringUtils.isEmpty(usernamePasswordFile));
  }
}
