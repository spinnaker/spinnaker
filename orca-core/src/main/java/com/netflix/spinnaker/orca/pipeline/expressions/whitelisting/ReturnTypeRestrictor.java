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

import java.util.*;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public interface ReturnTypeRestrictor extends InstantiationTypeRestrictor {
  Set<Class<?>> allowedReturnTypes = Collections.unmodifiableSet(
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
        ExecutionStatus.class,
        Execution.AuthenticationDetails.class,
        Execution.PausedDetails.class
      )
    )
  );

  static boolean supports(Class<?> type) {
    final Class<?> returnType = type.isArray() ? type.getComponentType() : type;
    return returnType.isPrimitive() || InstantiationTypeRestrictor.supports(returnType) || allowedReturnTypes.contains(returnType);
  }
}
