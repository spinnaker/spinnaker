/*
 * Copyright 2018 Netflix, Inc.
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
 */

package com.netflix.spinnaker.echo.jira;

import com.netflix.spinnaker.echo.controller.EchoResponse;
import retrofit.http.Body;
import retrofit.http.POST;

import java.util.HashMap;
import java.util.Map;

public interface JiraService {
  @POST("/rest/api/2/issue/")
  CreateJiraIssueResponse createJiraIssue(@Body CreateJiraIssueRequest createJiraIssueRequest);

  class CreateJiraIssueRequest extends HashMap<String, Object> {
    CreateJiraIssueRequest(Map<String, Object> body) {
      super(body);
    }
  }

  class CreateJiraIssueResponse implements EchoResponse.EchoResult {
    private String id;
    private String key;
    private String self;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getSelf() {
      return self;
    }

    public void setSelf(String self) {
      this.self = self;
    }
  }
}
