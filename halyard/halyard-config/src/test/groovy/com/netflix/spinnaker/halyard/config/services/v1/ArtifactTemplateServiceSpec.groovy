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

package com.netflix.spinnaker.halyard.config.services.v1

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException
import spock.lang.Specification

class ArtifactTemplateServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  LookupService getMockLookupService(String config) {
      def lookupService = new LookupService()
      lookupService.parser = mocker.mockHalconfigParser(config)
      return lookupService
  }

  ArtifactTemplateService makeArtifactTemplateService(String config) {
      def lookupService = getMockLookupService(config)
      def deploymentService = new DeploymentService()
      deploymentService.lookupService = lookupService
      new ArtifactTemplateService(lookupService, new ValidateService(), deploymentService)
  }

  def "load an existing artifact template node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  artifacts:
    templates:
    - name: test-template
      templatePath: /home/user/test-template.jinja
"""
    def artifactTemplateService = makeArtifactTemplateService(config)

    when:
    def result = artifactTemplateService.getAllArtifactTemplates(DEPLOYMENT)

    then:
    result != null
    result.size() == 1
    result[0].getName() == "test-template"
    result[0].getTemplatePath() == "/home/user/test-template.jinja"

    when:
    result = artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "test-template")

    then:
    result != null
    result.getName() == "test-template"
    result.getTemplatePath() == "/home/user/test-template.jinja"

    when:
    artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "non-existent-template")

    then:
    thrown(ConfigNotFoundException)
  }

  def "no error if templates is empty"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  artifacts:
    templates: []
"""
    def artifactTemplateService = makeArtifactTemplateService(config)

    when:
    def result = artifactTemplateService.getAllArtifactTemplates(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:
    artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "test-template")

    then:
    thrown(ConfigNotFoundException)
  }

  def "no error if templates is missing"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  artifacts:
"""
    def artifactTemplateService = makeArtifactTemplateService(config)

    when:
    def result = artifactTemplateService.getAllArtifactTemplates(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:
    artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "test-template")

    then:
    thrown(ConfigNotFoundException)
  }

  def "multiple templates are correctly parsed"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  artifacts:
    templates:
    - name: test-template
      templatePath: /home/user/test-template.jinja
    - name: test-template-2
      templatePath: /home/user/test-template-2.jinja
"""
    def artifactTemplateService = makeArtifactTemplateService(config)

    when:
    def result = artifactTemplateService.getAllArtifactTemplates(DEPLOYMENT)

    then:
    result != null
    result.size() == 2

    when:
    result = artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "test-template")

    then:
    result != null
    result.getName() == "test-template"
    result.getTemplatePath() == "/home/user/test-template.jinja"

    when:
    result = artifactTemplateService.getArtifactTemplate(DEPLOYMENT, "test-template-2")

    then:
    result != null
    result.getName() == "test-template-2"
    result.getTemplatePath() == "/home/user/test-template-2.jinja"
  }
}
