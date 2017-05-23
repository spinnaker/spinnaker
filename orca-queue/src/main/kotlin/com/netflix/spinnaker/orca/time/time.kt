package com.netflix.spinnaker.orca.time

import java.time.Duration
import java.time.Instant

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

fun Long?.toInstant(): Instant? = if (this == null) null else Instant.ofEpochMilli(this)

fun Long.toDuration(): Duration = Duration.ofMillis(this)
