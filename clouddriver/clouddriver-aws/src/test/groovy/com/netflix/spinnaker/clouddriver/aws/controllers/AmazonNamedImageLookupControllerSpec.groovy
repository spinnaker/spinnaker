/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.clouddriver.aws.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.controllers.AmazonNamedImageLookupController.LookupOptions
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.servlet.http.HttpServletRequest

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

class AmazonNamedImageLookupControllerSpec extends Specification {

  void "should extract tags from query parameters"() {
    given:
    def httpServletRequest = httpServletRequest(["tag:tag1": "value1", "tag:Tag2": "value2"])

    expect:
    AmazonNamedImageLookupController.extractTagFilters(httpServletRequest) == ["tag1": "value1", "tag2": "value2"]
  }

  void "should support filtering on 1..* tags"() {
    given:
    def namedImage1 = new AmazonNamedImageLookupController.NamedImage(
      amis: ["us-east-1": ["ami-123"]],
      tagsByImageId: ["ami-123": ["state": "released"]]
    )
    def namedImage2 = new AmazonNamedImageLookupController.NamedImage(
      amis: ["us-east-1": ["ami-456"]],
      tagsByImageId: ["ami-456": ["state": "released", "engine": "spinnaker"]]
    )

    and:
    def controller = new AmazonNamedImageLookupController(null)

    expect:
    // single tag ... matches all
    controller.filter([namedImage1, namedImage2], ["state": "released"]) == [namedImage1, namedImage2]

    // multiple tags ... matches one (case-insensitive)
    controller.filter([namedImage1, namedImage2], ["STATE": "released", "engine": "SpinnakeR"]) == [namedImage2]

    // single tag ... matches none
    controller.filter([namedImage1, namedImage2], ["xxx": "released"]) == []

    // no tags ... matches all
    controller.filter([namedImage1, namedImage2], [:]) == [namedImage1, namedImage2]
  }

  @Unroll
  void "should prevent searches on bad ami-like query: #query"() {
    given:
    def controller = new AmazonNamedImageLookupController(null)

    when:
    controller.validateLookupOptions(new LookupOptions(q: query))

    then:
    thrown(InvalidRequestException)

    where:
    query << ["ami", "ami-", "ami-1234", "ami-123456789"]
  }

  @Unroll
  void "should not throw exception when performing acceptable ami-like query: #query"() {
    given:
    def controller = new AmazonNamedImageLookupController(Stub(Cache))

    when:
    controller.validateLookupOptions(new LookupOptions(q: query))

    then:
    notThrown(InvalidRequestException)

    where:
    query << ["ami_", "ami-12345678", "sami", "ami_12345678", "ami-1234567890abcdef0"]
  }

  void "find by ami id interacts with the cache as expected"() {
    given:
    def httpServletRequest = httpServletRequest([:])
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def account = 'account'
    def region = 'region'
    def imageTags = [att1: 'value1', att2: 'value2']
    def query = amiId
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageId = Keys.getImageKey(amiId, account, region)
    def namedImageId = Keys.getNamedImageKey(account, amiName)
    def tagsAsAttributes = imageTags.collect { key, value -> [key: key, value: value] }
    def virtualizationType = 'virtualizationType'
    def architecture = 'architecture'
    def creationDate = 'creationDate'
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageId,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributes,
                                                                      imageId: amiId,
                                                                      virtualizationType: virtualizationType,
                                                                      architecture: architecture,
                                                                      creationDate: creationDate],
                                                                     [(NAMED_IMAGES.ns): [namedImageId]])]

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect no identifier lookup by name since we query for an AMI id
    0 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _)

    // Expect to lookup image identifiers
    1 * cacheView.filterIdentifiers(IMAGES.ns, _) >> [amiId]

    // Expect a lookup by name, but with no items to look in since we query for
    // an AMI id
    1 * cacheView.getAll(NAMED_IMAGES.ns, [], _) >> []

    // Expect a lookup by image
    1 * cacheView.getAll(IMAGES.ns, _) >> imageCacheData

    // And then in render, expect another image lookup, this time with no images
    // to look in because we're not querying by name.
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      attributes == [virtualizationType: virtualizationType, creationDate: creationDate, architecture: architecture]
      tagsByImageId == [(amiId): imageTags]
      accounts == [account] as Set
      amis == [region: [amiId] as Set]
      tags == imageTags
    }
  }

  void "find by name interacts with the cache as expected"() {
    given:
    def httpServletRequest = httpServletRequest([:])
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def account = 'account'
    def region = 'region'
    def imageTags = [att1: 'value1', att2: 'value2']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageId = Keys.getImageKey(amiId, account, region)
    def namedImageId = Keys.getNamedImageKey(account, amiName)
    def tagsAsAttributes = imageTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageId,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributes,
                                                                      imageId: amiId],
                                                                     [(NAMED_IMAGES.ns): [namedImageId]])]
    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageId,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageId]])]

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named image is related to a "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageId]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiId): imageTags]
      accounts == [account] as Set
      amis == [region: [amiId] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by name when two amis in the same region have the same name"() {
    given:
    def httpServletRequest = httpServletRequest([:])
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def account = 'account'
    def region = 'region'
    def imageOneTags = [att1: 'value1', att2: 'value2']
    def imageTwoTags = [att3: 'value3', att3: 'value3']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, account, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, account, region)
    def namedImageId = Keys.getNamedImageKey(account, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesOne,
                                                                      imageId: amiIdOne],
                                                                     [(NAMED_IMAGES.ns): [namedImageId]]),
                                                new DefaultCacheData(imageIdTwo,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesTwo,
                                                                      imageId: amiIdTwo],
                                                                     [(NAMED_IMAGES.ns): [namedImageId]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageId,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdOne, imageIdTwo]])]

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named image is related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiIdOne): imageOneTags, (amiIdTwo): imageTwoTags]
      accounts == [account] as Set
      amis == [region: [amiIdOne, amiIdTwo] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  private HttpServletRequest httpServletRequest(Map<String, String> tagFilters) {
    return Mock(HttpServletRequest) {
      getParameterNames() >> {
        new Vector(["param1"] + tagFilters.keySet()).elements()
      }
      getParameter(_) >> { String key -> tagFilters.get(key) }
    }
  }
}
