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

package com.netflix.spinnaker.orca.peering

/**
 * Interface that custom peerers should implement (and expose as a bean)
 */
interface CustomPeerer {
  /**
   * Let the custom peerer initialize itself
   *
   * @param srcDb source/foreign (read-only) database, use srcDb.runQuery to get the jooq context to perform queries on
   * @param destDb destination/local database
   * @param peerId the id of the peer we are peering
   */
  fun init(srcDb: SqlRawAccess, destDb: SqlRawAccess, peerId: String)

  /**
   * doPeer function will be called AFTER all default peering actions are completed for this agent cycle
   *
   * @return true if peering completed successfully, false otherwise
   */
  fun doPeer(): Boolean
}
