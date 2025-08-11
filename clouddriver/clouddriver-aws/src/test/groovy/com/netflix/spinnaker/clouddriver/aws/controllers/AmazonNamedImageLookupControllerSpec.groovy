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
import com.netflix.spinnaker.clouddriver.aws.controllers.AmazonNamedImageLookupController.LookupOptions
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

class AmazonNamedImageLookupControllerSpec extends Specification {

  void "should extract tags from query parameters"() {
    given:
    def httpServletRequest = httpServletRequest(["tag:tag1": "value1", "tag:Tag2": "value2"])

    expect:
    AmazonNamedImageLookupController.extractTagFilters(httpServletRequest) == ["tag1": "value1", "tag2": "value2"]
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

  void "find by - name and tags - two amis - same account - same region - same name - different ids - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def account = 'account'
    def region = 'region'

    // The order doesn't matter here.  If either image doesn't have that satisfy
    // the ones asked for in the query, the test fails.
    def imageOneTags = [att1: 'value1', att2: 'value2']
    def imageTwoTags = [:]
    def soughtAfterTags = [att1: 'value1']

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
                                     architecture: 'architecture', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageId,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdOne, imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

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
      tagsByImageId == [(amiIdOne): imageOneTags]
      accounts == [account] as Set
      amis == [region: [amiIdOne] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name - two amis - same account - same region - same name - different ids - different tags"() {
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
    def imageTwoTags = [att3: 'value3']
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

  void "find by - name and tags - two amis - different accounts - same name - different ids - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1']
    def imageTwoTags = [:]
    def soughtAfterTags = [att1: 'value1']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesOne,
                                                                      imageId: amiIdOne],
                                                                     [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesTwo,
                                                                      imageId: amiIdTwo],
                                                                     [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiIdOne): imageOneTags]

      accounts == [accountOne] as Set
      amis == [myRegion: [amiIdOne] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name and tags - two amis - different accounts - same name - same id - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1']
    def imageTwoTags = [:]
    def soughtAfterTags = [att1: 'value1']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }

    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesOne,
                                                                      imageId: amiId],
                                                                     [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                                     [name: amiName,
                                                                      tags: tagsAsAttributesTwo,
                                                                      imageId: amiId],
                                                                     [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                                          namedImageCacheAttributes,
                                                                          [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiId): imageOneTags]

      accounts == [accountOne] as Set
      amis == [myRegion: [amiId] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - id and tags - two amis - different accounts - same name - same id - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1']
    def imageTwoTags = [:]
    def soughtAfterTags = [att1: 'value1']
    def query = amiId
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }

    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiId,
                                                   virtualizationType: 'hvm', // arbitrary
                                                   architecture: 'architecture', // arbitrary
                                                   creationDate: '2021-08-03T22:27:50.000Z'], // arbitrary
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiId,
                                                   virtualizationType: 'hvm', // arbitrary
                                                   architecture: 'architecture', // arbitrary
                                                   creationDate: '2021-08-03T22:27:50.000Z'], // arbitrary
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     architecture: 'architecture', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                         namedImageCacheAttributes,
                                                         [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                         namedImageCacheAttributes,
                                                         [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // list() - Expect no identifier lookup by name.
    0 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _)

    // list() - Expect image identifiers since query is by image ID.
    1 * cacheView.filterIdentifiers(IMAGES.ns, _) >> [amiId]

    // list() - Expect no lookup by name since query by image id.
    1 * cacheView.getAll(NAMED_IMAGES.ns, [], _) >> []

    // list() - Expect a lookup by queried image id.
    1 * cacheView.getAll(IMAGES.ns, [amiId]) >> imageCacheData

    // render() - Expect a look up by image id but with an empty set of ids.
    // images has been passed in but not namedImages, and ids are stripped from namedImages.
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiId): imageOneTags]

      accounts == [accountOne] as Set
      amis == [myRegion: [amiId] as Set]
      // render() only populates this field when query by id.
      tags == imageOneTags
    }
  }

  void "find by - name - two amis - different accounts - same name - same id - different tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1', att2: 'value2']
    def imageTwoTags = [att3: 'value3']
    def soughtAfterTags = [:]
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiId],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiId],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     architecture: 'architecture', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      // The data structure is limited here.  We'd need tagsByAccountAndImageId
      // to include all the information.  So, the code arbitrarily chooses to
      // use the tags for the first image it processes.
      tagsByImageId == [(amiId): imageOneTags]

      // Preserve both accounts.  This is inconsistent, as tagsByImageId only
      // contains information from accountOne.  This is what the results showed
      // before all these changes, and it's not clear how to properly fix this,
      // so retain the behavior.  Querying for images in a specific account is a
      // way to bypass the struggle here.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiId] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - id - two amis - different accounts - same name - same id - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [:]
    def imageTwoTags = [att1: 'value1']
    def soughtAfterTags = [:]
    def query = amiId
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }

    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
      [name: amiName,
       tags: tagsAsAttributesOne,
       imageId: amiId,
       virtualizationType: 'hvm', // arbitrary
       architecture: 'architecture', // arbitrary
       creationDate: '2021-08-03T22:27:50.000Z'], // arbitrary
      [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiId,
                                                   virtualizationType: 'hvm', // arbitrary
                                                   architecture: 'architecture', // arbitrary
                                                   creationDate: '2021-08-03T22:27:50.000Z'], // arbitrary
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     architecture: 'architecture', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
      namedImageCacheAttributes,
      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // list() - Expect no identifier lookup by name.
    0 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _)

    // list() - Expect image identifiers since query is by image ID.
    1 * cacheView.filterIdentifiers(IMAGES.ns, _) >> [amiId]

    // list() - Expect no lookup by name since query by image id.
    1 * cacheView.getAll(NAMED_IMAGES.ns, [], _) >> []

    // list() - Expect a lookup by queried image id.
    1 * cacheView.getAll(IMAGES.ns, [amiId]) >> imageCacheData

    // render() - Expect a look up by image id but with an empty set of ids.
    // images has been passed in but not namedImages, and ids are stripped from namedImages.
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]

      // Preserve the set of non-empty tags.
      tagsByImageId == [(amiId): imageTwoTags]

      // Preserve both accounts.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiId] as Set]
      // render() only populates this field when query by id.
      tags == imageTwoTags
    }
  }

  void "find by - name and tags - two amis - different accounts - same name - different ids - same tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1']
    def imageTwoTags = [att1: 'value1']
    def soughtAfterTags = [att1: 'value1']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiIdOne],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiIdTwo],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiIdOne): imageOneTags, (amiIdTwo): imageTwoTags]

      // Preserve both accounts.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiIdOne, amiIdTwo] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name and tags - two amis - different accounts - same name - different ids - no tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [:]
    def imageTwoTags = [:]
    def soughtAfterTags = [att1: 'value1']
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiIdOne],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiIdTwo],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    // Nothing should match. There are images with queried name, but none have tags.
    // Adding a tag filter to the original query should remove all images with no tags.
    results.size() == 0
  }

  void "find by - name - two amis - different accounts - same name - same id - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [:]
    def imageTwoTags = [att1: 'value1']
    def soughtAfterTags = [:]
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                [name: amiName,
                                                 tags: tagsAsAttributesOne,
                                                 imageId: amiId],
                                                [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiId],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiId): imageTwoTags]

      // Preserve both accounts.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiId] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name - two amis - different accounts - same name - different ids - only one has tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [att1: 'value1']
    def imageTwoTags = [:]
    def soughtAfterTags = [:]
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiIdOne],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiIdTwo],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiIdOne): imageOneTags, (amiIdTwo): imageTwoTags]

      // Preserve both accounts.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiIdOne, amiIdTwo] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name - two amis - different accounts - same name - same id - no tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiId = 'ami-12345678'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [:]
    def imageTwoTags = [:]
    def soughtAfterTags = [:]
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiId, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiId, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiId],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiId],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      // No tags should be present.
      tagsByImageId == [(amiId): [:]]

      // Both accounts should be preserved.
      accounts == [accountOne, accountTwo] as Set
      amis == [myRegion: [amiId] as Set]
      // When there's a named image that matches the given query, render doesn't
      // currently populate tags, only tagsByImageId, as tags is deprecated.
      tags == [:]
    }
  }

  void "find by - name - two amis - different accounts - same name - different ids - no tags"() {
    given:
    Cache cacheView = Mock(Cache)
    def controller = new AmazonNamedImageLookupController(cacheView)
    def amiIdOne = 'ami-12345678'
    def amiIdTwo = 'ami-5678abcd'
    def amiName = 'myAmi'
    def accountOne = 'accountOne'
    def accountTwo = 'accountTwo'
    def region = 'myRegion'
    def imageOneTags = [:]
    def imageTwoTags = [:]
    def soughtAfterTags = [:]
    def query = amiName
    // Yes, this is insanely detailed, but it's what the render method expects
    // (and what ImageCachingAgent provides).
    def imageIdOne = Keys.getImageKey(amiIdOne, accountOne, region)
    def imageIdTwo = Keys.getImageKey(amiIdTwo, accountTwo, region)
    def namedImageIdOne = Keys.getNamedImageKey(accountOne, amiName)
    def namedImageIdTwo = Keys.getNamedImageKey(accountTwo, amiName)
    def tagsAsAttributesOne = imageOneTags.collect { key, value -> [key: key, value: value] }
    def tagsAsAttributesTwo = imageTwoTags.collect { key, value -> [key: key, value: value] }
    def Collection<CacheData> imageCacheData = [new DefaultCacheData(imageIdOne,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesOne,
                                                   imageId: amiIdOne],
                                                  [(NAMED_IMAGES.ns): [namedImageIdOne]]),
                                                new DefaultCacheData(imageIdTwo,
                                                  [name: amiName,
                                                   tags: tagsAsAttributesTwo,
                                                   imageId: amiIdTwo],
                                                  [(NAMED_IMAGES.ns): [namedImageIdTwo]])]

    def namedImageCacheAttributes = [name: amiName,
                                     virtualizationType: 'hvm', // arbitrary
                                     creationDate: '2021-08-03T22:27:50.000Z'] // arbitrary

    def Collection<CacheData> namedImageCacheData = [new DefaultCacheData(namedImageIdOne,
                                                      namedImageCacheAttributes,
                                                      [(IMAGES.ns): [imageIdOne]]),
                                                     new DefaultCacheData(namedImageIdTwo,
                                                       namedImageCacheAttributes,
                                                       [(IMAGES.ns): [imageIdTwo]])]

    def tagQueryParam = soughtAfterTags.collectEntries { key, value -> ["tag:${key}".toString(), value.toString()] }
    def httpServletRequest = httpServletRequest(tagQueryParam)

    when:
    List<AmazonNamedImageLookupController.NamedImage> results = controller.list(new LookupOptions(q: query), httpServletRequest)

    then:
    // Expect an identifier lookup by name
    1 * cacheView.filterIdentifiers(NAMED_IMAGES.ns, _) >> [amiName]

    // Expect no image identifiers since the identifier lookup by name returned
    // something
    0 * cacheView.filterIdentifiers(IMAGES.ns, _)

    // Expect a lookup by name, with the one available name, and return the ami
    // from each account
    1 * cacheView.getAll(NAMED_IMAGES.ns, [amiName], _) >> namedImageCacheData

    // Expect a lookup by image, but with no items to look in since the
    // identifier lookup by name returned something
    1 * cacheView.getAll(IMAGES.ns, []) >> []

    // And then in render, expect another image lookup, this time with an image
    // id because our named images are related to at least one "real" image.
    1 * cacheView.getAll(IMAGES.ns, [imageIdOne, imageIdTwo]) >> imageCacheData

    and:
    results.size() == 1
    with(results[0]) {
      imageName == amiName
      // When there's a named image that matches the given query, these are the
      // attributes that render populates.
      attributes == namedImageCacheAttributes - [name: amiName]
      tagsByImageId == [(amiIdOne): imageOneTags, (amiIdTwo): imageTwoTags]

      // Both accounts should be preserved.
      accounts == [accountOne, accountTwo] as Set
      // Both ids should be preserved.
      amis == [myRegion: [amiIdOne, amiIdTwo] as Set]
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
