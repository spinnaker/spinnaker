/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;

import java.util.ArrayList;
import java.util.List;

abstract public class AbstractConfigCommand extends NestableCommand {
  @Parameter(names = { "--no-validate" }, description = "Skip validation.")
  public boolean noValidate = false;

  protected String getCurrentDeployment() {
    return Daemon.getCurrentDeployment();
  }

  protected static boolean isSet(String s) {
    return s != null && !s.isEmpty();
  }

  /**
   * Provides consistent update semantics for updating a list of entries given a new list, entries to remove, and entries to add.
   *
   * @param old is the prior set of entries - these are modified to addTo and removeFrom.
   * @param setTo is a new set of entries. If provided, the old ones are discarded.
   * @param addTo is a single entry to add to old.
   * @param removeFrom is a single entry to remove from old.
   * @return the updated set of entries.
   * @throws IllegalArgumentException when setTo and (addTo or removeFrom) are provided.
   */
  protected static List<String> updateStringList(List<String> old, List<String> setTo, String addTo, String removeFrom) {
    if (old == null) {
      old = new ArrayList<>();
    }

    boolean set = setTo != null && !setTo.isEmpty();
    boolean add = addTo != null && !addTo.isEmpty();
    boolean remove = removeFrom != null && !removeFrom.isEmpty();

    if (set && (add || remove)) {
      throw new IllegalArgumentException("If set is specified, neither addTo nor removeFrom can be specified");
    }

    if (set) {
      return setTo;
    } else {
      if (add) {
        old.add(addTo);
      }

      if (remove) {
        old.remove(removeFrom);
      }

      return old;
    }
  }

  protected static boolean isSet(Object o) {
    return o != null;
  }
}
