/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetContainer

interface AmazonAssetHandler<S> {
  /**
   * Converts an [assetContainer] to a single [Asset].
   *
   * In most cases, this will just return the [assetContainer] root asset, but if there are any partial assets
   * included, those will need to be merged into the final asset. This is meant for convergence as well as
   * fingerprinting by the rule engine.
   */
  fun flattenAssetContainer(assetContainer: AssetContainer): Asset

  /**
   * Retrieve the current state for the provided asset based on the [spec].
   */
  fun current(spec: S, request: AssetContainer): Asset?

  /**
   * Converge on the provided asset.
   *
   * Implementors are expected to have already used [flattenAssetContainer] prior to calling this method.
   * [assetId] is provided for use in correlation IDs.
   */
  fun converge(assetId: String, spec: S)
}
