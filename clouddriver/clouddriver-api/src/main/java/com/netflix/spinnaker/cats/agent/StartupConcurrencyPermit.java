/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.cats.agent;

/**
 * Objects implementing this interface signal that a Long running agent have acquired the permission
 * to run a costly operation. You can release the acquired permission by calling `close`. It should
 * also be noted that it is advised to use this object in try-with-resource statements to avoid
 * boilerplate code.
 */
public interface StartupConcurrencyPermit extends AutoCloseable {
  void close();
}
