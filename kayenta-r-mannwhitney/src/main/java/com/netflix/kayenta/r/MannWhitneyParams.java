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

import lombok.Builder;
import lombok.Getter;

@Builder
public class MannWhitneyParams {
  @Getter
  private double mu;

  @Getter
  private double confidenceLevel;

  @Getter
  private double[] controlData;

  @Getter
  private double[] experimentData;

  String toCommandString(String controlName, String experimentName) {
    String ret = "wilcox.test(" + experimentName + "," + controlName;
    ret += ",conf.int=TRUE,mu=" + mu;
    ret += ",conf.level=" + confidenceLevel;
    ret += ")";
    return ret;
  }

  private void appendVector(StringBuilder ret, double[] data) {
    ret.append("c(");
    boolean first = true;
    for (double datum: data) {
      if (!first)
        ret.append(",");
      first = false;
      ret.append(datum);
    }
    ret.append(")");
  }
}
