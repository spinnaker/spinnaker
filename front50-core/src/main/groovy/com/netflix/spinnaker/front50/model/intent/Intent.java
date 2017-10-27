/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.front50.model.intent;

import com.netflix.spinnaker.front50.model.Timestamped;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class Intent extends HashMap<String, Object> implements Timestamped {

  private static final Logger log = LoggerFactory.getLogger(PipelineTemplate.class);

  @Override
  public String getId() {
    return (String) super.get("id");
  }

  public void setId(String id) {
    super.put("id", id);
  }

  @Override
  public Long getLastModified() {
    String updateTs = (String) super.get("updateTs");
    return (updateTs != null) ? Long.valueOf(updateTs) : null;
  }

  @Override
  public void setLastModified(Long lastModified) {
    super.put("updateTs", lastModified.toString());
  }

  @Override
  public String getLastModifiedBy() {
    return (String) super.get("lastModifiedBy");
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy);
  }

  public String getKind() {
    return (String) super.get("kind");
  }

  public void setKind(String kind) {
    super.put("kind", kind);
  }

  public String getSchema() {
    return (String) super.get("schema");
  }

  public void setSchema(String schema) {
    super.put("schema", schema);
  }

  public Object getSpec() {
    return super.get("spec");
  }

  public void setSpec(Object spec) {
    super.put("spec", spec);
  }

  public String getStatus() {
    return (String) super.get("status");
  }

  public void setStatus(String status) {
    super.put("status", status);
  }

}
