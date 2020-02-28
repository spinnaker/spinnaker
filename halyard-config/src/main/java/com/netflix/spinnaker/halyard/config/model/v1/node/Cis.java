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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.jenkins.JenkinsCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.wercker.WerckerCi;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Cis extends Node implements Cloneable {
  JenkinsCi jenkins = new JenkinsCi();
  TravisCi travis = new TravisCi();
  WerckerCi wercker = new WerckerCi();
  ConcourseCi concourse = new ConcourseCi();
  GoogleCloudBuild gcb = new GoogleCloudBuild();
  AwsCodeBuild codebuild = new AwsCodeBuild();

  public boolean ciEnabled() {
    NodeIterator iterator = getChildren();
    Ci child = (Ci) iterator.getNext();
    while (child != null) {
      if (child.isEnabled()) {
        return true;
      }

      child = (Ci) iterator.getNext();
    }

    return false;
  }

  @Override
  public String getNodeName() {
    return "ci";
  }

  public static Class<? extends Ci> translateCiType(String ciName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(Cis.class.getDeclaredFields())
            .filter(f -> f.getName().equals(ciName))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Ci>) res.get();
    } else {
      throw new IllegalArgumentException("No ci with name \"" + ciName + "\" handled by halyard");
    }
  }
}
