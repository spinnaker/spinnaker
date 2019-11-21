/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;
import retrofit.http.GET;
import retrofit.http.Query;

public interface SlackService {
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  class SlackChannelsResult {
    public List<Map> channels;
    public ResponseMetadata response_metadata;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  class ResponseMetadata {
    public String next_cursor;
  }

  @GET(
      "/conversations.list?limit=1000&exclude_archived=true&pretty=1") // https://api.slack.com/methods/conversations.list
  SlackChannelsResult getChannels(@Query("token") String token, @Query("cursor") String cursor);
}
