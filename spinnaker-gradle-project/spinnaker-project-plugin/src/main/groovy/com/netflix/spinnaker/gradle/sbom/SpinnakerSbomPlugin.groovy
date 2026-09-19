/*
 * Copyright 2026 Spinnaker Authors
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

package com.netflix.spinnaker.gradle.sbom

import org.cyclonedx.gradle.CyclonedxPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Generates a CycloneDX SBOM for a Spinnaker service.
 *
 * Applying the upstream plugin once (it is applied to every project via
 * SpinnakerBaseProjectPlugin) registers a "cyclonedxDirectBom" task per project and a
 * "cyclonedxBom" task that aggregates every subproject's direct BOM into one document for
 * the project it's applied to. Running "cyclonedxBom" at a service's root therefore produces
 * a single SBOM covering that service, which is the unit Spinnaker actually ships (a container
 * image / deb), rather than one SBOM per Gradle module or one for the whole monorepo.
 */
class SpinnakerSbomPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(CyclonedxPlugin)
    }
}
