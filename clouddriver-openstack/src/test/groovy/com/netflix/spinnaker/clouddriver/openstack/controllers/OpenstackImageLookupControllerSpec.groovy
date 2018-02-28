/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.controllers

import com.google.common.collect.Sets
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import com.netflix.spinnaker.clouddriver.openstack.provider.ImageProvider
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.Void as Should
import java.util.regex.Pattern

class OpenstackImageLookupControllerSpec extends Specification {
  OpenstackImageLookupController controller
  ImageProvider imageProvider

  def setup() {
    imageProvider = Mock(ImageProvider)
    controller = new OpenstackImageLookupController(imageProvider)
  }

  Should 'search for all images'() {
    given:
    String account = null
    String query = null
    String region = null

    OpenstackImage imageA = Mock(OpenstackImage)
    OpenstackImage imageB = Mock(OpenstackImage)
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    3 * imageA.name >> 'image'
    3 * imageB.name >> 'image'
    result == Sets.union(imageSetA, imageSetB)
    noExceptionThrown()
  }

  Should 'search for all images - no images'() {
    given:
    String account = null
    String query = null
    String region = null

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> [:]
    result == [] as Set
    noExceptionThrown()
  }

  Should 'search for images by account only'() {
    given:
    String account = 'test'
    String query = null
    String region = null

    OpenstackImage image = Mock(OpenstackImage)
    Set<OpenstackImage> imageSet = Sets.newHashSet(image)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [(account): imageSet]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    2 * image.name >> 'image'
    result == imageSet
    noExceptionThrown()
  }

  Should 'search for images by account only - not found'() {
    given:
    String account = 'test'
    String query = null
    String region = null

    OpenstackImage image = Mock(OpenstackImage)
    Set<OpenstackImage> imageSet = Sets.newHashSet(image)
    Map<String, Set<OpenstackImage>> imagesByAccounts = ['stage': imageSet]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    0 * image.name >> 'image'
    result == [] as Set
    noExceptionThrown()
  }

  Should 'search for images query only'() {
    given:
    String account = null
    String query = 'im'
    String region = null

    OpenstackImage imageA = Mock(OpenstackImage) { getName() >> 'imageA' }
    OpenstackImage imageB = Mock(OpenstackImage) { getName() >> 'mock' }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == imageSetA
    noExceptionThrown()
  }

  Should 'search for images query only - not found'() {
    given:
    String account = null
    String query = 'tes'
    String region = null

    OpenstackImage imageA = Mock(OpenstackImage) { getName() >> 'imageA' }
    OpenstackImage imageB = Mock(OpenstackImage) { getName() >> 'mock' }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == [] as Set
    noExceptionThrown()
  }

  Should 'search for images account and query'() {
    given:
    String account = 'accountA'
    String query = 'im'
    String region = null

    OpenstackImage imageA = Mock(OpenstackImage) { getName() >> 'imageA' }
    OpenstackImage imageB = Mock(OpenstackImage) { getName() >> 'mock' }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA, imageB)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == Sets.newHashSet(imageA)
    noExceptionThrown()
  }

  Should 'search for images account and query - not found'() {
    given:
    String account = 'accountA'
    String query = 'tes'
    String region = null

    OpenstackImage imageA = Mock(OpenstackImage) { getName() >> 'imageA' }
    OpenstackImage imageB = Mock(OpenstackImage) { getName() >> 'mock' }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA, imageB)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == [] as Set
    noExceptionThrown()
  }

  Should 'search for images throw exception'() {
    given:
    String account = 'accountA'
    String query = 'tes'
    String region = null

    Throwable throwable = new JedisException('exception')

    when:
    controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    thrownException == throwable
  }

  Should 'search for images account, query, and region'() {
    given:
    String account = 'accountA'
    String query = 'im'
    String region = 'east'

    OpenstackImage imageA = Mock(OpenstackImage) {
      getName() >> 'imageA'
      getRegion() >> 'east'
    }
    OpenstackImage imageB = Mock(OpenstackImage) {
      getName() >> 'mock'
      getRegion() >> 'central'
    }
    OpenstackImage imageC = Mock(OpenstackImage) {
      getName() >> 'imageC'
      getRegion() >> 'east'
    }

    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA, imageB)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageC)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == Sets.newHashSet(imageA)
    noExceptionThrown()
  }

  Should 'search for images account, query, and region - not found'() {
    given:
    String account = 'accountA'
    String query = 'im'
    String region = 'east'

    OpenstackImage imageA = Mock(OpenstackImage) {
      getName() >> 'imageA'
      getRegion() >> 'central'
    }
    OpenstackImage imageB = Mock(OpenstackImage) {
      getName() >> 'mock'
      getRegion() >> 'east'
    }
    OpenstackImage imageC = Mock(OpenstackImage) {
      getName() >> 'imageC'
      getRegion() >> 'east'
    }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA, imageB)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageC)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == [] as Set
    noExceptionThrown()
  }

  Should 'search for images query and region'() {
    given:
    String account = null
    String query = 'im'
    String region = 'east'

    OpenstackImage imageA = Mock(OpenstackImage) {
      getName() >> 'imageA'
      getRegion() >> 'east'
    }
    OpenstackImage imageB = Mock(OpenstackImage) {
      getName() >> 'imageB'
      getRegion() >> 'central'
    }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == imageSetA
    noExceptionThrown()
  }

  Should 'search for images query and region - not found'() {
    given:
    String account = null
    String query = 'im'
    String region = 'east'

    OpenstackImage imageA = Mock(OpenstackImage) {
      getName() >> 'imageA'
      getRegion() >> 'central'
    }
    OpenstackImage imageB = Mock(OpenstackImage) {
      getName() >> 'mock'
      getRegion() >> 'central'
    }
    Set<OpenstackImage> imageSetA = Sets.newHashSet(imageA)
    Set<OpenstackImage> imageSetB = Sets.newHashSet(imageB)
    Map<String, Set<OpenstackImage>> imagesByAccounts = [accountA: imageSetA, accountB: imageSetB]

    when:
    Set<OpenstackImage> result = controller.find(account, query, region)

    then:
    1 * imageProvider.listImagesByAccount() >> imagesByAccounts
    result == [] as Set
    noExceptionThrown()
  }

  @Unroll
  Should 'resolve to pattern - #testCase'() {
    when:
    Pattern result = controller.resolveQueryToPattern(query)

    then:
    result.pattern() == expected

    where:
    testCase     | query    | expected
    'default'    | null     | '.*'
    'normal'     | 'ubuntu' | '.*\\Qubuntu\\E.*'
    'wildcard 1' | 'ub*'    | '\\Qub\\E.*'
    'wildcard 2' | '*test'  | '.*\\Qtest\\E'
  }
}
