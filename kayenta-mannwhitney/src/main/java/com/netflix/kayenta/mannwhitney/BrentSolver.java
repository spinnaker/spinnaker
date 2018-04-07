/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.kayenta.mannwhitney;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.util.Precision;

public class BrentSolver {

  static private final int MAX_ITERATIONS = 1000;
  static private final double TOLERANCE = 1E-4;
  static private final double EPSILON = 2.220446049250313E-16;

  /**
   * Search for a zero inside the provided interval.
   *
   * @param ax Lower bound of the search interval.
   * @param bx Higher bound of the search interval.
   * @param fa Function value at the lower bound of the search interval.
   * @param fb Function value at the higher bound of the search interval.
   * @param func The function we are finding roots of.
   * @return the value where the function is zero.
   */

  static double solve(double ax, double bx, double fa, double fb,
                      UnivariateFunction func)
  {
    double a, b, c, fc;
    int iteration = MAX_ITERATIONS + 1;

    a = ax;
    b = bx;
    c = a;
    fc = fa;

    // First test if we have found a root at an endpoint.
    if (Precision.equals(fa, 0)) {
      return a;
    }
    if (Precision.equals(fb, 0)) {
      return b;
    }

    while (iteration-- > 0)	{
      double prev_step = b - a;
      double tol_act;
      double p;
      double q;
      double new_step;

      if (Math.abs(fc) < Math.abs(fb)) {
        a = b;
        b = c;
        c = a;
        fa = fb;
        fb = fc;
        fc = fa;
      }
      tol_act = 2 * EPSILON * Math.abs(b) + TOLERANCE / 2;
      new_step = (c - b) / 2;

      if (Math.abs(new_step) <= tol_act || Precision.equals(fb, 0)) {
        return b;
      }

      if (Math.abs(prev_step) >= tol_act && Math.abs(fa) > Math.abs(fb) ) {
        double t1, cb, t2;
        cb = c - b;
        if (a == c) {
          t1 = fb / fa;
          p = cb * t1;
          q = 1.0 - t1;
        } else {			// Quadric inverse interpolation
          q = fa / fc;
          t1 = fb / fc;
          t2 = fb / fa;
          p = t2 * (cb * q * (q - t1) - (b - a) * (t1 - 1.0));
          q = (q - 1.0) * (t1 - 1.0) * (t2 - 1.0);
        }
        if (p > 0.0)
          q = -q;
        else
          p = -p;

        if (p < (0.75 * cb * q - Math.abs(tol_act * q) / 2) && p < Math.abs(prev_step * q / 2))
          new_step = p / q;
      }

      if (Math.abs(new_step) < tol_act) {
        if (new_step > 0)
          new_step = tol_act;
        else
          new_step = -tol_act;
      }
      a = b;
      fa = fb;
      b += new_step;
      fb = func.value(b);
      if ((fb > 0 && fc > 0) || (fb < 0 && fc < 0)) {
        c = a;
        fc = fa;
      }
    }

    // failed!
    // TODO: should throw exception.
    return b;
  }
}
