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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters()
public abstract class AbstractEditAccountCommand<T extends Account> extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract Account editAccount(T account);

  public String getDescription() {
    return "Edit a " + getProviderName() + " account.";
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Account account = Daemon.getAccount(currentDeployment, providerName, accountName, false);

    Daemon.setAccount(currentDeployment, providerName, accountName, !noValidate, editAccount((T) account));
    AnsiUi.success("Edited " + providerName + " account \"" + accountName + "\"");
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
}
