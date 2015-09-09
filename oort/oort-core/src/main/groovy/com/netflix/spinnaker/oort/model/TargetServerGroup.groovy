/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.model

/**
 * Targets are dynamically resolved Server Groups.
 */
enum TargetServerGroup {
  /**
   * The most recent Server Group.
   */
  CURRENT,

  /**
   * The second most recent Server Group. Useful for referencing the Server Group to which to roll back.
   */
  PREVIOUS,

  /**
   * The oldest Server Group.
   */
  OLDEST,

  // Legacy suffixes - used to determine when targets were resolved (during setup or execution, respectively).
  @Deprecated private static String asg = "_asg"
  @Deprecated private static String asgDynamic = "_asg_dynamic"

  public static TargetServerGroup fromString(String s) {
    for (TargetServerGroup t : TargetServerGroup.values()) {
      def n = t.name()
      if (n.equalsIgnoreCase(s) ||
          "${n}${asg}".equalsIgnoreCase(s) ||
          "${n}${asgDynamic}".equalsIgnoreCase(s)) {
        return t
      }
    }
    throw new IllegalArgumentException("No TargetServerGroup enum matched '${s}'")
  }
}
