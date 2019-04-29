/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class V3Log {
  private Integer id;
  private String content;

  @JsonProperty("log_parts")
  private List<V3LogPart> logParts;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getContent() {
    if (content != null) {
      return content;
    } else if (logParts != null && !logParts.isEmpty()) {
      return logParts.stream().map(V3LogPart::getContent).collect(Collectors.joining());
    } else {
      return null;
    }
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setLogParts(List<V3LogPart> logParts) {
    this.logParts = logParts;
  }

  public boolean isReady() {
    if (logParts == null || logParts.isEmpty()) {
      return false;
    }
    int numberOfParts = logParts.size() - 1;
    V3LogPart lastLogPart = logParts.get(numberOfParts);
    return numberOfParts == lastLogPart.number && lastLogPart.isFinal();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class V3LogPart {
    private String content;
    private Integer number;
    private boolean isFinal;

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }

    public Integer getNumber() {
      return number;
    }

    public void setNumber(Integer number) {
      this.number = number;
    }

    public boolean isFinal() {
      return isFinal;
    }

    public void setFinal(boolean isFinal) {
      this.isFinal = isFinal;
    }
  }
}
