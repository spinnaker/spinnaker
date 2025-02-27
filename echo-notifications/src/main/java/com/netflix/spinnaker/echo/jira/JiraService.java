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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface JiraService {
  @POST("rest/api/2/issue/")
  Call<CreateIssueResponse> createIssue(@Body CreateIssueRequest createIssueRequest);

  @GET("rest/api/2/issue/{issueIdOrKey}/transitions")
  Call<IssueTransitions> getIssueTransitions(@Path("issueIdOrKey") String issueIdOrKey);

  @POST("rest/api/2/issue/{issueIdOrKey}/transitions")
  Call<ResponseBody> transitionIssue(
      @Path("issueIdOrKey") String issueIdOrKey,
      @Body TransitionIssueRequest transitionIssueRequest);

  @POST("rest/api/2/issue/{issueIdOrKey}/comment")
  Call<ResponseBody> addComment(
      @Path("issueIdOrKey") String issueIdOrKey, @Body CommentIssueRequest commentIssueRequest);

  class CreateIssueRequest extends HashMap<String, Object> {
    CreateIssueRequest(Map<String, Object> body) {
      super(body);
    }
  }

  class TransitionIssueRequest extends HashMap<String, Object> {
    TransitionIssueRequest(Map<String, Object> body) {
      super(body);
    }
  }

  class CommentIssueRequest {
    private String body;

    CommentIssueRequest(String body) {
      this.body = body;
    }

    public String getBody() {
      return body;
    }

    public void setBody(String body) {
      this.body = body;
    }
  }

  class IssueTransitions {
    private List<Transition> transitions;

    public List<Transition> getTransitions() {
      return transitions;
    }

    public void setTransitions(List<Transition> transitions) {
      this.transitions = transitions;
    }

    public static class Transition {
      private String id;
      private String name;

      public String getId() {
        return id;
      }

      public void setId(String id) {
        this.id = id;
      }

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }
    }
  }

  class CreateIssueResponse implements EchoResponse.EchoResult {
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
