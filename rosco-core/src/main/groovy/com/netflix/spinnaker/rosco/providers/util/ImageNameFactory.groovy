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

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest

interface ImageNameFactory {

  /**
   * This method is responsible for:
   *   - Producing an image name.
   *   - (If one or more package names are specified) Attempting to derive the AppVersion descriptor from the
   *     first package name. If the AppVersion could not be derived, this value will be null.
   *
   * This method always returns a list of size 3 with the following elements:
   *   1) A derived image name (to be used for naming the image being baked).
   *   2) The appversion string (to be used for tagging the newly-baked image).
   *   3) The updated list of packages to be used for overriding the passed package list (this will be removed once the
   *      temporary workaround described above is removed).
   *
   * This function is not required to return the same image name on multiple invocations with the same bake request.
   *
   * Returns [imageName, appVersionStr, packagesParameter].
   */
  def deriveImageNameAndAppVersion(BakeRequest bakeRequest, BakeOptions.Selected selectedOptions)

}
