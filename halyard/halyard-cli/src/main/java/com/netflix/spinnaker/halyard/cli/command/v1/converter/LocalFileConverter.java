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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.converter;

import com.beust.jcommander.IStringConverter;
import com.netflix.spinnaker.halyard.core.GlobalApplicationOptions;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.io.File;
import java.io.IOException;
import org.aspectj.util.FileUtil;

public class LocalFileConverter implements IStringConverter<String> {

  private static final String CONFIG_SERVER_PREFIX = "configserver:";

  @Override
  public String convert(String value) {
    if (EncryptedSecret.isEncryptedSecret(value) || isConfigServerResource(value)) {
      return value;
    }

    if (GlobalApplicationOptions.getInstance().isUseRemoteDaemon()) {
      try {
        return FileUtil.readAsString(new File(value));
      } catch (IOException e) {
        throw new HalException(
            Problem.Severity.FATAL,
            "Was passed parameter " + value + " to unreadable file: " + e.getMessage());
      }
    }
    return new File(value).getAbsolutePath();
  }

  private boolean isConfigServerResource(String value) {
    return value.startsWith(CONFIG_SERVER_PREFIX);
  }
}
