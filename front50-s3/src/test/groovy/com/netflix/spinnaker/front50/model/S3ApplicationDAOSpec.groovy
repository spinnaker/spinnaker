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


package com.netflix.spinnaker.front50.model

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.utils.S3TestHelper
import rx.schedulers.Schedulers
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.util.concurrent.Executors

@IgnoreIf({ S3TestHelper.s3ProxyUnavailable() })
class S3ApplicationDAOSpec extends Specification {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))
  def objectMapper = new ObjectMapper()
  def amazonS3 = new AmazonS3Client(new ClientConfiguration())
  def s3ApplicationDAO = new S3ApplicationDAO(objectMapper, amazonS3, scheduler, 0, "front50", "test")

  void setup() {
    amazonS3.setEndpoint("http://127.0.0.1:9999")
    S3TestHelper.setupBucket(amazonS3, "front50")
  }

  def "should support standard create/refresh/findAll/delete behaviors"() {
    given:
    def application = new Application([
        name       : "app1",
        description: "My description"
    ])
    s3ApplicationDAO.create("app1", application)

    expect:
    toMap(s3ApplicationDAO.findByName("app1")) == toMap(application)

    when:
    s3ApplicationDAO.refresh()

    then:
    s3ApplicationDAO.all().collect { toMap(it) } == [toMap(application)]

    when:
    s3ApplicationDAO.delete(application.id)
    s3ApplicationDAO.findByName(application.name)

    then:
    thrown(NotFoundException)

    when:
    s3ApplicationDAO.refresh()

    then:
    s3ApplicationDAO.all().isEmpty()
  }

  private Map toMap(Application application) {
    def map = objectMapper.convertValue(application, Map)
    map.remove("updateTs")
    map.remove("createTs")
    return map
  }
}
