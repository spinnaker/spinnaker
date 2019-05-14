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

package com.netflix.spinnaker.orca.pipeline.expressions.whitelisting;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public interface ReturnTypeRestrictor extends InstantiationTypeRestrictor {
  Set<Class<?>> allowedReturnTypes =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  Collection.class,
                  Map.class,
                  SortedMap.class,
                  List.class,
                  Set.class,
                  SortedSet.class,
                  ArrayList.class,
                  LinkedList.class,
                  HashSet.class,
                  LinkedHashSet.class,
                  HashMap.class,
                  LinkedHashMap.class,
                  TreeMap.class,
                  TreeSet.class,
                  Execution.class,
                  Stage.class,
                  Trigger.class,
                  BuildInfo.class,
                  JenkinsArtifact.class,
                  JenkinsBuildInfo.class,
                  ConcourseBuildInfo.class,
                  SourceControl.class,
                  ExecutionStatus.class,
                  Execution.AuthenticationDetails.class,
                  Execution.PausedDetails.class)));

  static boolean supports(Class<?> type) {
    final Class<?> returnType = type.isArray() ? type.getComponentType() : type;
    return returnType.isPrimitive()
        || InstantiationTypeRestrictor.supports(returnType)
        || allowedReturnTypes.contains(returnType);
  }
}
