/*
 * Copyright 2019 Armory, Inc.
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
package com.netflix.spinnaker.kork.secrets

import java.net.URI

/**
 * Base exception type for errors related to the format of an invalid [SecretReference].
 */
open class InvalidSecretFormatException : SecretException {
  constructor() : super()

  constructor(message: String?) : super(message)

  constructor(cause: Throwable?) : super(cause)

  constructor(message: String?, cause: Throwable?) : super(message, cause)

  constructor(message: String, secretReference: URI) : this(message) {
    additionalAttributes["secretReference"] = secretReference
  }
}
