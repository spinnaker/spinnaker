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

package com.netflix.spinnaker.halyard.cli.ui.v1;

/**
 * A shared colored terminal output class
 */
public class Ui {
  private Color color;

  public Ui(boolean colorEnabled) {
    this.color = new Color(colorEnabled);
  }

  private void reset(String message) {
    try {
      System.out.print(message);
    } catch (Exception e) {
      throw e;
    } finally {
      System.out.println(color.RESET);
    }
  }

  public void raw(String message) {
    reset(message);
  }

  public void info(String message) {
    reset(color.BOLD + color.BLUE + ". " + color.RESET + message);
  }

  public void warning(String message) {
    reset(color.BOLD + color.YELLOW + "- " + color.RESET + message);
  }

  public void failure(String message) {
    reset(color.BOLD + color.RED + "! " + color.RESET + message);
  }

  public void remediation(String message) {
    reset(color.BOLD + color.MAGENTA + "? " + color.RESET + message);
  }

  public void success(String message) {
    reset(color.BOLD + color.GREEN + "+ " + color.RESET + message);
  }
}
