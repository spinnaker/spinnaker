/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel

import com.netflix.spinnaker.keel.exceptions.DeclarativeException

interface AssetLauncher<out R : LaunchedAssetResult> {

  fun launch(asset: Asset<AssetSpec>): R

  fun <I : Asset<AssetSpec>> assetProcessor(assetProcessors: List<AssetProcessor<*>>, asset: I)
    = assetProcessors.find { it.supports(asset) }.let {
    if (it == null) {
      throw DeclarativeException("Could not find processor for asset ${asset.javaClass.simpleName}")
    }
    // TODO rz - GROSS AND WRONG
    return@let it as AssetProcessor<I>
  }
}

interface LaunchedAssetResult
