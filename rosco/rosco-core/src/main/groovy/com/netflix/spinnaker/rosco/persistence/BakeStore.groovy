/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.persistence

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.jobs.BakeRecipe

/**
 * Persistence service for in-flight and completed bakes.
 */
interface BakeStore {

  /**
   * Used to ensure that only one bake per bake key is initiated at a time.
   */
  public boolean acquireBakeLock(String bakeKey)

  /**
   * Store the region, bakeRecipe, bakeRequest and bakeStatus in association with both the bakeKey and bakeId. If bake key
   * has already been set, return a bakeStatus with that bake's id instead. None of the arguments may be null.
   */
  public BakeStatus storeNewBakeStatus(String bakeKey, String region, BakeRecipe bakeRecipe, BakeRequest bakeRequest, BakeStatus bakeStatus, String command)

  /**
   * Update the completed bake details associated with both the bakeKey and bakeDetails.id. bakeDetails may not be null.
   */
  public void updateBakeDetails(Bake bakeDetails)

  /**
   * Update the bakeStatus associated with both the bakeKey and bakeStatus.id. If bakeStatus.state is neither
   * pending nor running, remove bakeStatus.id from the set of incomplete bakes. bakeStatus may not be null.
   */
  public void updateBakeStatus(BakeStatus bakeStatus)

  /**
   * Store the error in association with both the bakeId and the bakeKey. Neither argument may be null.
   */
  public void storeBakeError(String bakeId, String error)

  /**
   * Retrieve the region specified with the original request that is associated with the bakeId. bakeId may be null.
   */
  public String retrieveRegionById(String bakeId)

  /**
   * Retrieve the cloud provider specified with the original request that is associated with the bakeId.
   */
  public String retrieveCloudProviderById(String bakeId)

  /**
   * Retrieve the bake status associated with the bakeKey. bakeKey may be null.
   */
  public BakeStatus retrieveBakeStatusByKey(String bakeKey)

  /**
   * Retrieve the bake status associated with the bakeId. bakeId may be null.
   */
  public BakeStatus retrieveBakeStatusById(String bakeId)

  /**
   * Retrieve the bake request associated with the bakeId.
   */
  public BakeRequest retrieveBakeRequestById(String bakeId)

  /**
   * Retrieve the bake recipe associated with the bakeId.
   */
  public BakeRecipe retrieveBakeRecipeById(String bakeId)

  /**
   * Retrieve the completed bake details associated with the bakeId. bakeId may be null.
   */
  public Bake retrieveBakeDetailsById(String bakeId)

  /**
   * Retrieve the logs associated with the bakeId. bakeId may be null.
   */
  public Map<String, String> retrieveBakeLogsById(String bakeId)

  /**
   * Delete the bake status, completed bake details and logs associated with the bakeKey. If the bake is still
   * incomplete, remove the bake id from the set of incomplete bakes. Returns the bake id of the deleted bake or null
   * if it is not found.
   */
  public String deleteBakeByKey(String bakeKey)

  /**
   * Delete the bake status associated with the bake key. If the bake is still incomplete, remove the bake id from the
   * set of incomplete bakes and mark the bake CANCELED. Leave behind any completed bake details and logs associated
   * with the bake id resolved from the bake key. Returns the bake id of the deleted bake or null if it is not found.
   */
  public String deleteBakeByKeyPreserveDetails(String bakeKey)

  /**
   * Delete the bake entities associated with the given pipeline execution id.
   */
  void deleteBakeByPipelineExecutionId(String pipelineExecutionId);

  /**
   * Cancel the incomplete bake associated with the bake id and delete the completed bake details associated with the
   * bake id. If the bake is still incomplete, remove the bake id from the set of incomplete bakes.
   */
  public boolean cancelBakeById(String bakeId)

  /**
   * Remove the incomplete bake from the rosco instance's set of incomplete bakes.
   */
  public void removeFromIncompletes(String roscoInstanceId, String bakeId)

  /**
   * Retrieve the set of incomplete bake ids for this rosco instance.
   */
  public Set<String> getThisInstanceIncompleteBakeIds()

  /**
   * Retrieve a map of rosco instance ids -> sets of incomplete bake ids.
   */
  public Map<String, Set<String>> getAllIncompleteBakeIds()

  public void saveImageToBakeRelationship(String region, String image, String bakeId)

  public String getBakeIdFromImage(String region, String image)

  /**
   * Get the current redis server time in milliseconds.
   */
  public long getTimeInMilliseconds()
}
