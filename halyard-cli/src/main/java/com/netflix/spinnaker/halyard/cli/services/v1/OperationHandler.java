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

package com.netflix.spinnaker.halyard.cli.services.v1;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.NONE;

import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiPrinter;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import java.util.function.Supplier;
import lombok.Data;

@Data
public class OperationHandler<T> implements Supplier<T> {
  String successMessage;
  String failureMesssage;
  Supplier<T> operation;
  Format format = NONE;
  boolean userFormatted = false;

  @Override
  public T get() {
    T res;
    try {
      res = operation.get();
    } catch (ExpectedDaemonFailureException e) {
      throw new ExpectedDaemonFailureException(failureMesssage, e.getCause());
    } catch (TaskKilledException e) {
      throw TaskKilledException.extendMessage(failureMesssage, e);
    }

    if (successMessage != null && !GlobalOptions.getGlobalOptions().isQuiet()) {
      AnsiUi.success(successMessage);
    }

    if (userFormatted) {
      Format userFormat = GlobalOptions.getGlobalOptions().getOutput();
      if (userFormat != null) {
        format = userFormat;
      }
    }

    String result = AnsiFormatUtils.format(format, res);
    if (!result.isEmpty()) {
      AnsiPrinter.out.println(result);
    }

    return res;
  }
}
