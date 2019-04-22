/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gradle.license

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.CsvReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.JsonReportRenderer

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ProjectReportsPlugin

import static com.github.jk1.license.filter.LicenseBundleNormalizer.*

class SpinnakerLicenseReportPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(ProjectReportsPlugin)
        project.plugins.apply(LicenseReportPlugin)

        LicenseReportExtension pluginConfig = project.extensions.getByType(LicenseReportExtension)
        pluginConfig.configurations = LicenseReportExtension.ALL
        pluginConfig.renderers = [
          new InventoryHtmlReportRenderer(),
          new CsvReportRenderer(),
          new JsonReportRenderer() ]

        def licenseBundleNormalizer = new LicenseBundleNormalizer()
        addSpinnakerNormalizerBundle(licenseBundleNormalizer.normalizerConfig)
        pluginConfig.filters = [ licenseBundleNormalizer ]
    }

    def addSpinnakerNormalizerBundle(LicenseBundleNormalizerConfig config) {
        def additionalConfig = new JsonSlurper().parse(getClass().getResourceAsStream("/license-normalizer-bundle.json"))

        additionalConfig.bundles.each { Map bundle ->
            config.bundles.add(new NormalizerLicenseBundle(bundle))
        }

        additionalConfig.transformationRules.each { Map transformationRule ->
            config.transformationRules.add(new NormalizerTransformationRule(transformationRule))
        }
    }
}
