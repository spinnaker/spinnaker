/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers

import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcrImageProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsImagesControllerSpec extends Specification {
  def ecrImageProvider = Mock(EcrImageProvider)
  @Subject
  def controller = new EcsImagesController([ecrImageProvider])

  def 'should retrieve image details based on tagged url'() {
    given:
    def tag = 'latest'
    def region = 'us-west-1'
    def repoName = 'test-repo'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag

    def expectedMaps = [[
                          region    : region,
                          imageName : accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest,
                          amis      : ['us-west-1': Collections.singletonList(digest)],
                          attributes: [creationDate: new Date()]
                        ]]

    ecrImageProvider.getRepositoryName() >> 'ECR'
    ecrImageProvider.handles(url) >> true
    ecrImageProvider.findImage(url) >> expectedMaps

    when:
    def retrievedMap = controller.findImage(url, null)

    then:
    retrievedMap == expectedMaps
  }
}
