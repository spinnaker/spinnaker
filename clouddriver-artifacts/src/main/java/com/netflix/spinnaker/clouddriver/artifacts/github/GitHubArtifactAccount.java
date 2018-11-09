/*
 * Copyright 2017 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.github;


import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import lombok.Data;

@Data
public class GitHubArtifactAccount implements ArtifactAccount {
  private String name;
  /*
    One of the following are required for auth:
     - username and password
     - usernamePasswordFile : path to file containing "username:password"
     - token
     - tokenFile : path to file containing token
   */
  private String username;
  private String password;
  private String usernamePasswordFile;
  private String token;
  private String tokenFile;
}
