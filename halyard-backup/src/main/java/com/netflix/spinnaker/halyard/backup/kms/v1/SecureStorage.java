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
 *
 *
 */

package com.netflix.spinnaker.halyard.backup.kms.v1;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;

public abstract class SecureStorage {
  protected abstract void storeContents(String name, String contents);

  public void backupFile(String name, File file) {
    String contents;
    try {
      contents = IOUtils.toString(new FileInputStream(file));
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Can't load file for secure storage: " + e.getMessage(), e);
    }

    storeContents(name, contents);
  }
}
