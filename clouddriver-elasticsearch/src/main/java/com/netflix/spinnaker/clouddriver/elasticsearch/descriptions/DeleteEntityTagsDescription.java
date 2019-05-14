/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.descriptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.security.resources.NonCredentialed;
import java.util.List;

public class DeleteEntityTagsDescription implements NonCredentialed {
  @JsonProperty private String id;

  @JsonProperty private List<String> tags;

  @JsonProperty private boolean deleteAll = false;

  public String getId() {
    return id;
  }

  public List<String> getTags() {
    return tags;
  }

  public boolean isDeleteAll() {
    return deleteAll;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public void setDeleteAll(boolean deleteAll) {
    this.deleteAll = deleteAll;
  }
}
