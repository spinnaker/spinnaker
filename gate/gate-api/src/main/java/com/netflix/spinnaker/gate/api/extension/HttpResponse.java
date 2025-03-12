/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.api.extension;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.Map;
import lombok.Data;

/**
 * An {@link HttpResponse} represents the response from a request that has been handled by an {@link
 * ApiExtension}.
 */
@Alpha
@Data(staticConstructor = "of")
public class HttpResponse {
  private final int status;
  private final Map<String, String> headers;
  private final Object body;
}
