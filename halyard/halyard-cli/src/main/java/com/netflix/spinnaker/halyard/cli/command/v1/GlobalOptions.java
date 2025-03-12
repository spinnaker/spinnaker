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

package com.netflix.spinnaker.halyard.cli.command.v1;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import lombok.Data;
import org.slf4j.LoggerFactory;

/** This is the collection of general, top-level flags to be interpreted by halyard. */
@Data
public class GlobalOptions {
  private boolean color = true;

  private boolean debug = false;

  private boolean help = false;

  private boolean quiet = false;

  private boolean alpha = false;

  private String daemonEndpoint = "http://localhost:8064";

  private String options;

  private Level log;

  private AnsiFormatUtils.Format output;

  private static GlobalOptions globalOptions = null;

  public void setLog(Level log) {
    this.log = log;

    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(log);
  }

  public static boolean isGlobalOption(String name) {
    int i = 0;
    while (name.charAt(i) == '-') {
      i++;
    }

    name = name.substring(i);
    i = name.indexOf("-");
    if (i != -1) {
      String prefix = name.substring(0, i);
      String suffix = name.substring(i + 1, name.length());
      name = prefix + suffix.substring(0, 1).toUpperCase() + suffix.substring(1, suffix.length());
    }
    try {
      GlobalOptions.class.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      return false;
    }
    return true;
  }

  public static GlobalOptions getGlobalOptions() {
    if (globalOptions == null) {
      globalOptions = new GlobalOptions();
    }

    return globalOptions;
  }

  private GlobalOptions() {
    setLog(Level.OFF);
  }
}
