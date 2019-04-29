/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.model;

import java.util.Date;
import java.util.Map;
import lombok.*;
import org.springframework.http.HttpHeaders;

/** Represents event metadata */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Metadata {
  private String source;
  private String type;
  private String created = Long.toString(new Date().getTime());
  private String organization;
  private String project;
  private String application;
  private String _content_id;
  private Map attributes;
  private HttpHeaders requestHeaders = new HttpHeaders();
}
