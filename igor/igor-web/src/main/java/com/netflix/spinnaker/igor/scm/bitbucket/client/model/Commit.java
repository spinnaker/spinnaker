/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm.bitbucket.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Commit {
  private String hash;
  private Author author;
  private String html_href;
  private Date date;
  private String message;

  @JsonProperty("links")
  public void setUser(Map<String, Object> links) {
    Object html = links.get("html");
    if (html instanceof Map) {
      Map<String, Object> htmlMap = (Map<String, Object>) html;
      Object href = htmlMap.get("href");
      if (href != null) {
        html_href = href.toString();
      }
    }
  }

  @JsonProperty(value = "date")
  public void setDate(String utctimestamp) {
    date = Date.from(ZonedDateTime.parse(utctimestamp).toInstant());
  }
}
