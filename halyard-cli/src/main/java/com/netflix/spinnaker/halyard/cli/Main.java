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

package com.netflix.spinnaker.halyard.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.cli.command.v1.HalCommand;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;

public class Main {
  public static void main(String[] args) {
    GlobalOptions globalOptions = GlobalOptions.getGlobalOptions();

    Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));

    HalCommand hal = new HalCommand();
    JCommander jc = new JCommander(hal);
    hal.setCommander(jc).configureSubcommands();

    try {
      jc.parse(args);
    } catch (IllegalArgumentException e) {
      AnsiUi.error("Illegal argument: " + e.getMessage());
      System.exit(1);
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    try {
      hal.execute();
    } catch (IllegalArgumentException e) {
      AnsiUi.error("Illegal argument: " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      if (globalOptions.isDebug()) {
        e.printStackTrace();
      }

      AnsiUi.error(e.getMessage());
      AnsiUi.remediation(
          "That wasn't supposed to happen.\nPlease report an issue on https://github.com/spinnaker/halyard/issues");
      System.exit(1);
    }
  }
}
