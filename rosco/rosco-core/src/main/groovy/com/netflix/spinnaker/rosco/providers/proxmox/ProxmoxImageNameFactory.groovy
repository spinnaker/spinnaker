/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.rosco.providers.proxmox

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter

/**
 * Produces VM names that satisfy Proxmox's hostname requirements: lowercase letters, digits, and
 * hyphens only, max 63 characters, no leading or trailing hyphens.
 */
class ProxmoxImageNameFactory extends ImageNameFactory {

  @Override
  def buildImageName(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {
    String raw = super.buildImageName(bakeRequest, osPackageNames) as String
    return sanitize(raw)
  }

  private static String sanitize(String name) {
    name.toLowerCase()
        .replaceAll(/[^a-z0-9-]/, '-')   // replace anything not dns-safe with a hyphen
        .replaceAll(/-{2,}/, '-')          // collapse consecutive hyphens
        .replaceAll(/^-+|-+$/, '')         // strip leading/trailing hyphens
        .take(63)
  }
}
