/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.r;

import org.rosuda.REngine.*;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

public class MannWhitney {

  private RConnection re;

  private void connect() throws RserveException {
    re = new RConnection("127.0.0.1", 8283);
  }

  synchronized public void disconnect() {
    if (re != null)
      re.close();
    re = null;
  }

  synchronized public MannWhitneyResult eval(MannWhitneyParams params) throws RExecutionException {
    if (re == null) {
      try {
        connect();
      } catch (RserveException e) {
        throw new RExecutionException("Unable to connect to RServe", e);
      }
    }

    try {
      re.assign("controlData", params.getControlData());
      re.assign("experimentData", params.getExperimentData());
      String command = params.toCommandString("controlData", "experimentData");
      REXP result = re.parseAndEval("try(eval(" + command + "),silent=TRUE)");
      if (result == null) {
        throw new RExecutionException("Failed to get a result from R for Mann-Whitney test. command:" + command);
      }
      if (result.inherits("try-error")) {
        throw new RExecutionException("Failed to get a result from R for Mann-Whitney test. command: " + command + ", error: " + result.asString());
      }

      RList list = result.asList();
      REXPDouble pValue = (REXPDouble)list.get("p.value");
      REXPDouble confInt = (REXPDouble)list.get("conf.int");
      REXPDouble estimate = (REXPDouble)list.get("estimate");

      return MannWhitneyResult.builder()
              .pValue(pValue.asDouble())
              .confidenceInterval(confInt.asDoubles())
              .estimate(estimate.asDouble())
              .build();
    } catch (REXPMismatchException e) {
      throw new RExecutionException("Could not parse result type from R", e);
    } catch (REngineException e) {
      throw new RExecutionException("Unable to communicate with R", e);
    }
  }
}
