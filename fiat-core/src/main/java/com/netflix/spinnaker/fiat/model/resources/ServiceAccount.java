/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

@Data
public class ServiceAccount implements GroupAccessControlled, Viewable {
  private final ResourceType resourceType = ResourceType.SERVICE_ACCOUNT;

  private String name;

  @JsonIgnore
  public List<String> getRequiredGroupMembership() {
    // There is a potential here for a naming collision where service account
    // "my-svc-account@abc.com" and "my-svc-account@xyz.com" each allow one another's users to use
    // their service account. In practice, though, I don't think this will be an issue.
    return Collections.singletonList(StringUtils.substringBefore(name, "@"));
  }

  @JsonIgnore
  public View getView() {
    return new View(this);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public static class View extends BaseView {
    private String name;

    public View(ServiceAccount serviceAccount) {
      this.name = serviceAccount.name;
    }
  }
}
