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

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static junit.framework.TestCase.assertEquals;

//
// to install Rserve on a mac:
//
// Use Homebrew to install R
//
// Run this inside R:
//
//    local({
//            flags="CPPFLAGS=-L/usr/local/opt/gettext/lib LDFLAGS=-I/usr/local/opt/gettext/include"
//            install.packages('Rserve', configure.args=flags)})
//

public class MannWhitneyTest {

  private static final double E = 0.000001;

  @Test
  @Category(CIIgnoreTest.class)
  public void testMannWhitney() throws RExecutionException {
    // TODO: (mgraff) This test requires a locally running Rserve process, so should be skipped on CIs for now
    MannWhitneyParams params = MannWhitneyParams.builder()
            .confidenceLevel(0.95)
            .controlData(new double[]{1.0, 2.0, 3.0})
            .experimentData(new double[]{2.0, 3.0, 4.0})
            .mu(0)
            .build();
    MannWhitney mw = new MannWhitney();
    MannWhitneyResult result = mw.eval(params);
    assertEquals(-1.0, result.getEstimate(),E);
    assertEquals(0.3686882693617814, result.getPValue(),E);
    assertEquals(-3.0, result.getConfidenceInterval()[0],E);
    assertEquals(1.0, result.getConfidenceInterval()[1],E);
  }
}
