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

package com.netflix.spinnaker.orca.libdiffs

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/*
 * Groovy implementation of python LooseVersion class. Not complete and does not support all the cases which
 * python class supports.
 */
class LooseVersion implements Comparable {

  static final Log log = LogFactory.getLog(LooseVersion.class)
  String version
  boolean invalid
  List versions

  LooseVersion(String version) {
    this.version = version
    parse()
  }

  private void parse() {
    versions = version?.split("\\.").toList()
    versions = versions.size() > 4 ? versions.subList(0, 4) : versions
    for (i in 0..3) {
      if (versions[i] == null) {
        versions[i] = 0
      } else {
        if (isInt(versions[i])) versions[i] = Integer.parseInt(versions[i]).toInteger()
      }
    }
  }

  private boolean isInt(String str) {
    try {
      Integer.parseInt(str)
      return true
    } catch (e) {
      return false
    }
  }

  @Override
  int compareTo(Object o) {
    LooseVersion rhs = (LooseVersion) o
    if (this.version == rhs.version) return 0
    for (i in 0..3) {
      try {
        if (versions[i] > rhs.versions[i]) return 1
        if (versions[i] < rhs.versions[i]) return -1
      } catch (Exception e) {
        //assume it's different
        return -1
      }
    }
    return 0
  }

  String toString() {
    return version
  }
}
