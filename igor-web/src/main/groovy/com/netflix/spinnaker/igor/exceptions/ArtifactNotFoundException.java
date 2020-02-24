/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.exceptions;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(NOT_FOUND)
public class ArtifactNotFoundException extends NotFoundException {
  public ArtifactNotFoundException(
      String master, String job, Integer buildNumber, String fileName) {
    super(
        String.format(
            "Could not find build artifact matching requested filename '%s' on '%s/%s' build %s",
            fileName, master, job, buildNumber));
  }
}
