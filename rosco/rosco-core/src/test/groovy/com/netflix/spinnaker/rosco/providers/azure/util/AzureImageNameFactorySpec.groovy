/*
 * Copyright 2022 Armory.
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

package com.netflix.spinnaker.rosco.providers.azure.util

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification

class AzureImageNameFactorySpec extends Specification implements TestDefaults {

    void "Should provide an image name based on base_name"() {
        setup:
        def imageNameFactory = new AzureImageNameFactory()
        def bakeRequest = new BakeRequest(
                package_name: "7zip",
                base_name: "my-base-image",
                base_label: BakeRequest.Label.candidate,
                os_type: BakeRequest.OsType.windows
        )
        def osPackages = parseNupkgOsPackageNames(bakeRequest.package_name)

        when:
        def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)

        then:
        imageName == "my-base-image-all-candidate-windows"
    }

    void "Should provide an image name based on package"() {
        setup:
        def imageNameFactory = new AzureImageNameFactory()
        def bakeRequest = new BakeRequest(
                package_name: "7zip",
                base_label: BakeRequest.Label.release,
                os_type: BakeRequest.OsType.linux
        )
        def osPackages = parseDebOsPackageNames(bakeRequest.package_name)

        when:
        def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)

        then:
        imageName == "7zip-all-release-linux"
    }

    void "Should provide a valid image name when base_name and package_name are empty"() {
        setup:
        def imageNameFactory = new AzureImageNameFactory()
        def bakeRequest = new BakeRequest(
                package_name: "",
                base_name: null,
                base_label: BakeRequest.Label.unstable,
                os_type: BakeRequest.OsType.windows
        )
        def osPackages = parseNupkgOsPackageNames(bakeRequest.package_name)

        when:
        def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)

        then:
        imageName == "all-unstable-windows"
    }
}
