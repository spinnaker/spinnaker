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

import com.netflix.spinnaker.halyard.config.model.v1.ci.jenkins.JenkinsCi;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = false)
public class Cis extends Node implements Cloneable {
  JenkinsCi jenkins = new JenkinsCi();

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public String getNodeName() {
    return "ci";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeReflectiveIterator(this);
  }

  public static Class<? extends Ci> translateCiType(String ciName) {
    Optional<? extends Class<?>> res = Arrays.stream(Cis.class.getDeclaredFields())
        .filter(f -> f.getName().equals(ciName))
        .map(Field::getType)
        .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Ci>)res.get();
    } else {
      throw new IllegalArgumentException("No Continous Integration service with name \"" + ciName + "\" handled by halyard");
    }
  }

  public static Class<? extends Master> translateMasterType(String ciName) {
    Class<? extends Ci> ciClass = translateCiType(ciName);

    String masterClassName = ciClass.getName().replaceAll("Ci", "Master");
    try {
      return (Class<? extends Master>) Class.forName(masterClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No master for class \"" + masterClassName + "\" found", e);
    }
  }
}
