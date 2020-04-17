/*
 *
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.kork.exceptions.UserException

/**
 * The regex provided produced too many capture groups.
 */
class InvalidRegexException(
  val regex: String,
  val tag: String
) : UserException("The provided regex ($regex) produced did not produce one capture group on tag $tag")

class NoDockerImageSatisfiesConstraints(
  val artifactName: String,
  val environment: String
) : ResourceCurrentlyUnresolvable("No docker image found for artifact $artifactName in $environment")
