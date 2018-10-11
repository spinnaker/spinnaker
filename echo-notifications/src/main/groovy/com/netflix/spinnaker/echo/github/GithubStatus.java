/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.github;

public class GithubStatus {
  private String state;
  private String target_url;
  private String description;
  private String context;

  public GithubStatus(String state, String targetUrl, String description, String context) {
    this.state = state;
    this.target_url = targetUrl;
    this.description = description;
    this.context = context;
  }

  public String getState() {
    return state;
  }

  public String getTarget_url() {
    return target_url;
  }

  public String getDescription() {
    return description;
  }

  public String getContext() {
    return context;
  }

}
