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

package com.netflix.spinnaker.time

import java.time.Duration
import java.time.Instant

fun Long?.toInstant(): Instant? = this?.toInstant()
fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

fun Long?.toDuration(): Duration? = if (this == null) null else Duration.ofMillis(this)
fun Long.toDuration(): Duration = Duration.ofMillis(this)
