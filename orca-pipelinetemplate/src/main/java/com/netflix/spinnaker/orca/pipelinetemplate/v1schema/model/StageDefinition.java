/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;

import java.util.List;
import java.util.Map;

public class StageDefinition implements NamedContent, Conditional {

  private String id;
  private InjectionRule inject;
  private String dependsOn; // TODO rz - comma-delimited for now
  private String type;
  private Map<String, Object> config;
  private List<Map<String, Object>> notifications;
  private String comments;
  private Object when;

  @Override
  public String getName() {
    return id;
  }

  public static class InjectionRule {

    private Boolean first;
    private Boolean last;
    private String before;
    private String after;

    public Boolean getFirst() {
      return first;
    }

    public void setFirst(Boolean first) {
      this.first = first;
    }

    public Boolean getLast() {
      return last;
    }

    public void setLast(Boolean last) {
      this.last = last;
    }

    public String getBefore() {
      return before;
    }

    public void setBefore(String before) {
      this.before = before;
    }

    public String getAfter() {
      return after;
    }

    public void setAfter(String after) {
      this.after = after;
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public InjectionRule getInject() {
    return inject;
  }

  public void setInject(InjectionRule inject) {
    this.inject = inject;
  }

  public String getDependsOn() {
    return dependsOn;
  }

  public void setDependsOn(String dependsOn) {
    this.dependsOn = dependsOn;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Map<String, Object>> notifications) {
    this.notifications = notifications;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public Object getWhen() {
    return when;
  }

  public void setWhen(Object when) {
    this.when = when;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StageDefinition that = (StageDefinition) o;

    return id != null ? id.equals(that.id) : that.id == null;

  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}
