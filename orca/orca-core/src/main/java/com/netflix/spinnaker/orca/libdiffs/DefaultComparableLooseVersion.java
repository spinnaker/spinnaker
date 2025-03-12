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

package com.netflix.spinnaker.orca.libdiffs;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class DefaultComparableLooseVersion implements ComparableLooseVersion {

  @Override
  public int compare(String lhsVersion, String rhsVersion) {
    return new LooseVersion(lhsVersion).compareTo(new LooseVersion(rhsVersion));
  }

  /**
   * Groovy implementation of python LooseVersion class. Not complete and does not support all the
   * cases which python class supports.
   */
  static class LooseVersion implements Comparable {

    String version;
    boolean invalid;
    Integer[] versions;

    LooseVersion(String version) {
      this.version = version;
      parse();
    }

    private void parse() {
      try {
        versions =
            stream(version.split("\\."))
                .map(Integer::parseInt)
                .collect(toList())
                .toArray(new Integer[4]);
      } catch (NumberFormatException e) {
        versions = new Integer[0];
      }
    }

    @Override
    public int compareTo(Object o) {
      LooseVersion rhs = (LooseVersion) o;
      if (this.version.equals(rhs.version)) return 0;
      for (int i = 0; i < 4; i++) {
        try {
          if (versions[i] > rhs.versions[i]) return 1;
          if (versions[i] < rhs.versions[i]) return -1;
        } catch (Exception e) {
          // assume it's different
          return -1;
        }
      }
      return 0;
    }

    public String toString() {
      return version;
    }
  }
}
