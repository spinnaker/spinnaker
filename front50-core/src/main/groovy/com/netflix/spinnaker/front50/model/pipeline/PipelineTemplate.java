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
package com.netflix.spinnaker.front50.model.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineTemplate extends HashMap<String, Object> implements Timestamped {

  private static final Logger log = LoggerFactory.getLogger(PipelineTemplate.class);

  @JsonIgnore
  @SuppressWarnings("unchecked")
  public List<String> getScopes() {
    Map<String, Object> metadata = (Map<String, Object>) super.get("metadata");
    if (metadata == null || metadata.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> scopes = (List<String>) metadata.get("scopes");
    return scopes != null ? scopes : Collections.emptyList();
  }

  /** @return Un-decorated MPT id. */
  public String undecoratedId() {
    return (String) super.get("id");
  }

  /** @return Decorated id with appended digest or tag. */
  @Override
  public String getId() {
    String digest = getDigest();
    String tag = getTag();
    String id = (String) super.get("id");
    if (StringUtils.isNotEmpty(digest)) {
      return String.format("%s@sha256:%s", id, digest);
    } else if (StringUtils.isNotEmpty(tag)) {
      return String.format("%s:%s", id, tag);
    } else {
      return id;
    }
  }

  public void setId(String id) {
    super.put("id", id);
  }

  public String getTag() {
    return (String) super.get("tag");
  }

  public void setTag(String tag) {
    super.put("tag", tag);
  }

  public String getDigest() {
    return (String) super.get("digest");
  }

  public void setDigest(String digest) {
    super.put("digest", digest);
  }

  @Override
  public Long getLastModified() {
    String updateTs = (String) super.get("updateTs");
    return (updateTs != null) ? Long.valueOf(updateTs) : null;
  }

  /**
   * Removes and returns last modified time from the pipeline template.
   *
   * @return last modified time of the pipeline template.
   */
  public Long removeLastModified() {
    String updateTs = (String) super.remove("updateTs");
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

  /**
   * Removes and returns user that last modified the pipeline template.
   *
   * @return user last modifying the pipeline template.
   */
  public String removeLastModifiedBy() {
    return (String) super.remove("lastModifiedBy");
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy);
  }

  public boolean containsAnyScope(List<String> scope) {
    for (String s : scope) {
      for (String s2 : getScopes()) {
        if (s.equalsIgnoreCase(s2)) {
          return true;
        }

        try {
          if (s.matches(s2)) {
            return true;
          }
        } catch (PatternSyntaxException e) {
          log.warn(
              "Invalid scope matching pattern (scope: '" + s2 + "', template: " + getId() + ")");
        }
      }
    }
    return false;
  }

  public String getSource() {
    return (String) super.get("source");
  }

  public void setSource(String source) {
    super.put("source", source);
  }
}
