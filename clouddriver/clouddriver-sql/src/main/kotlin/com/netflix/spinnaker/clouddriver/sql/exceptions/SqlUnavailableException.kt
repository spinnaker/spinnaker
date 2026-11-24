/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.sql.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * The database was still unreachable once retries were exhausted. Reported as 503 rather than the
 * default 500 so callers treat it as transient: Orca retries a 503 on any request method, but fails
 * the stage on a 500.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
class SqlUnavailableException(message: String, cause: Throwable) : RuntimeException(message, cause), SqlException
