/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import com.netflix.spinnaker.echo.model.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;


public class EventContent {
  private Event event;
  private String repo;
  private String sha;
  private String pipeline;
  private String stageName;
  private String executionId;
  private int stageIndex;

  public EventContent(Event event, String type) throws FieldNotFoundException {
    this.event = event;
    setRepo();
    setSha();
    setPipeline();
    setExecutionId();
    if (type.equals("stage")) {
      setStageName();
      setStageIndex();
    }
  }

  private void setRepo() throws FieldNotFoundException {
    repo = Optional.ofNullable(event.getContent())
      .map(content -> (Map) content.get("execution"))
      .map(execution -> (Map) execution.get("trigger"))
      .map(trigger -> (Map) trigger.get("buildInfo"))
      .map(buildInfo -> (String) buildInfo.get("name"))
      .orElseThrow(FieldNotFoundException::new);
  }

  private void setSha() throws FieldNotFoundException {
    sha = Optional.ofNullable(event.getContent())
      .map(content -> (Map) content.get("execution"))
      .map(execution -> (Map) execution.get("trigger"))
      .map(trigger -> (Map) trigger.get("buildInfo"))
      .map(buildInfo -> (List) buildInfo.get("scm"))
      .map(scm -> (Map) scm.get(0))
      .map(scm -> (String) scm.get("sha1"))
      .orElseThrow(FieldNotFoundException::new);
  }

  private void setPipeline() throws FieldNotFoundException {
    pipeline = Optional.ofNullable(event.getContent())
      .map(content -> (Map) content.get("execution"))
      .map(execution -> (String) execution.get("name"))
      .orElseThrow(FieldNotFoundException::new);
  }

  private void setStageName() throws FieldNotFoundException {
    String stageName = Optional.ofNullable(event.getContent())
      .map(content -> (String) content.get("name"))
      .orElse(null);

    if (stageName == null) {
      stageName = Optional.ofNullable(event.getContent())
        .map(content -> (Map) content.get("context"))
        .map(context -> (Map) context.get("stageDetails"))
        .map(stageDetails -> (String) stageDetails.get("name"))
        .orElseThrow(FieldNotFoundException::new);
    }
    this.stageName = stageName;
  }

  private void setStageIndex() throws FieldNotFoundException {
    List<Map> stages = Optional.ofNullable(event.getContent())
      .map(content -> (Map) content.get("execution"))
      .map(execution -> (List<Map>) execution.get("stages"))
      .orElseThrow(FieldNotFoundException::new);

    Map stage = stages.stream().filter(s -> s.get("name").equals(getStageName()))
      .findFirst()
      .orElseThrow(FieldNotFoundException::new);

    stageIndex = stages.indexOf(stage);
  }

  private void setExecutionId() throws FieldNotFoundException {
    executionId = Optional.ofNullable(event.getContent())
      .map(content -> (Map) content.get("execution"))
      .map(execution -> (String) execution.get("id"))
      .orElseThrow(FieldNotFoundException::new);
  }

  public String getRepo() {
    return repo;
  }

  public String getSha() {
    return sha;
  }

  public String getPipeline() {
    return pipeline;
  }

  public String getStageName() {
    return stageName;
  }

  public int getStageIndex() {
    return stageIndex;
  }

  public String getExecutionId() {
    return executionId;
  }
}
