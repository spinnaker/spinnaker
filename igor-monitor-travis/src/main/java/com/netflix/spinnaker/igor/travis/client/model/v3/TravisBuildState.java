/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.netflix.spinnaker.igor.build.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum TravisBuildState {
  created,
  started,
  passed,
  canceled,
  failed,
  errored;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public Result getResult() {
    switch (this) {
      case created:
        return Result.NOT_BUILT;
      case started:
        return Result.BUILDING;
      case passed:
        return Result.SUCCESS;
      case canceled:
        return Result.ABORTED;
      case failed:
      case errored:
        return Result.FAILURE;
      default:
        log.info("could not convert {}", this);
        throw new IllegalArgumentException(
            "state: " + this + " is not known to TravisResultConverter.");
    }
  }

  public boolean isRunning() {
    switch (this) {
      case created:
      case started:
        return true;
      default:
        return false;
    }
  }
}
