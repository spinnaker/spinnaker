/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.List;
import java.util.function.Predicate;

/** Builds a predicate that determines whether a given service account is accessible. */
public interface ServiceAccountPredicateProvider {
  /**
   * @param userId Identifier for the currently authenticated user
   * @param userRoles Roles for the currently authenticated user
   * @param isAdmin Whether the currently authenticated user is an administrator
   * @return true if access to service account should be granted, otherwise false
   */
  Predicate<ServiceAccount> get(String userId, List<String> userRoles, boolean isAdmin);
}
