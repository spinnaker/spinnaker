/*
 * Copyright 2017 bol.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm.gitlab.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Commit {
  private final String id;
  private final String authorName;
  private final Date authoredDate;
  private final String message;

  @JsonCreator
  public Commit(
      @JsonProperty("id") String id,
      @JsonProperty("author_name") String authorName,
      @JsonProperty("authored_date") Date authoredDate,
      @JsonProperty("message") String message) {
    this.id = id;
    this.authorName = authorName;
    this.authoredDate = authoredDate;
    this.message = message;
  }

  public String getId() {
    return id;
  }

  public String getAuthorName() {
    return authorName;
  }

  public Date getAuthoredDate() {
    return authoredDate;
  }

  public String getMessage() {
    return message;
  }
}
