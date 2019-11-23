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

package com.netflix.spinnaker.echo.extension.rest;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pf4j.ExtensionPoint;

@Alpha
public interface RestEventParser extends ExtensionPoint {

  /**
   * Parse an Event prior to POST to the configured URL
   *
   * @param event {@link Event}
   * @return Map, which conforms to the RestEventListener API
   */
  Map parse(Event event);

  @Data
  class Event {
    public Metadata details;
    public Map<String, Object> content;
    public String rawContent;
    public Map<String, Object> payload;
    public String eventId;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  class Metadata {
    private String source;
    private String type;
    private String created;
    private String organization;
    private String project;
    private String application;
    private String _content_id;
    private Map<String, List> requestHeaders;
    private Map<String, String> attributes;
  }
}
