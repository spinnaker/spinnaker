/*
 * Copyright 2021 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class Lifecycle {
  private final String type;
  private final Map<String, Object> data;

  public Lifecycle(
      Type type,
      DeployCloudFoundryServerGroupDescription.ApplicationAttributes applicationAttributes) {
    this.type = type.getValue();
    this.data =
        type.equals(Type.BUILDPACK)
            ? new BuildpackLifecycleBuilder<String, Object>()
                .putIfValueNotNull("buildpacks", applicationAttributes.getBuildpacks())
                .putIfValueNotNull("stack", applicationAttributes.getStack())
                .build()
            : Collections.emptyMap();
  }

  @Getter
  @AllArgsConstructor
  public enum Type {
    BUILDPACK("buildpack"),
    DOCKER("docker");
    private String value;
  }

  static class BuildpackLifecycleBuilder<K, V> extends ImmutableMap.Builder<K, V> {
    public BuildpackLifecycleBuilder<K, V> putIfValueNotNull(K key, V value) {
      if (value != null) super.put(key, value);
      return this;
    }
  }
}
