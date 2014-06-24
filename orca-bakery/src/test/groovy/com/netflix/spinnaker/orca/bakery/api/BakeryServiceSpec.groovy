/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.api

import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.bakery.api.Bake.Label
import com.netflix.spinnaker.orca.bakery.api.Bake.OperatingSystem
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.test.httpserver.HttpServerRule
import org.junit.Rule
import retrofit.RetrofitError
import retrofit.client.OkClient
import static com.google.common.net.HttpHeaders.LOCATION
import static java.net.HttpURLConnection.*
import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.RestAdapter.LogLevel.FULL

class BakeryServiceSpec extends Specification {

  @Rule HttpServerRule httpServer = new HttpServerRule()

  @Subject BakeryService bakery

  final region = "us-west-1"
  final bake = new Bake("rfletcher", "orca", Label.release, OperatingSystem.ubuntu)
  final bakePath = "/api/v1/$region/bake"
  final statusPath = "/api/v1/$region/status"
  final bakeId = "b-123456789"
  final statusId = "s-123456789"

  String bakeURI
  String statusURI

  def setup() {
    bakeURI = "$httpServer.baseURI$bakePath"
    statusURI = "$httpServer.baseURI$statusPath"

    bakery = new BakeryConfiguration(retrofitClient: new OkClient(), retrofitLogLevel: FULL)
      .bakery(newFixedEndpoint(httpServer.baseURI))
  }

  def "can lookup a bake status"() {
    given:
    httpServer.expect("GET", "$statusPath/$statusId").andRespond().withStatus(HTTP_OK).withJsonContent {
      state "COMPLETED"
      progress 100
      status "SUCCESS"
      code 0
      resource_uri "$bakeURI/$bakeId"
      uri "$statusURI/$statusId"
      id statusId
      attempts: 0
      ctime 1382310109766
      mtime 1382310294223
      messages(["amination success"])
    }

    expect:
    with(bakery.lookupStatus(region, statusId).toBlockingObservable().first()) {
      id == statusId
      state == BakeStatus.State.COMPLETED
    }
  }

  def "looking up an unknown status id will throw an exception"() {
    given:
    httpServer.expect("GET", "$statusPath/$statusId").andRespond().withStatus(HTTP_NOT_FOUND)

    when:
    bakery.lookupStatus(region, statusId).toBlockingObservable().first()

    then:
    def ex = thrown(RetrofitError)
    ex.response.status == HTTP_NOT_FOUND
  }

  def "should return status of newly created bake"() {
    given: "the bakery accepts a new bake"
    httpServer.expect("POST", bakePath).andRespond().withStatus(HTTP_ACCEPTED).withJsonContent {
      state "PENDING"
      progress 0
      resource_uri "$bakeURI/$bakeId"
      uri "$statusURI/$statusId"
      id statusId
      attempts 0
      ctime 1382310109766
      mtime 1382310109766
      messages([])
    }

    expect: "createBake should return the status of the bake"
    with(bakery.createBake(region, bake).toBlockingObservable().first()) {
      id == statusId
      state == BakeStatus.State.PENDING
    }
  }

  def "should handle a repeat create bake response"() {
    given: "the POST to /bake redirects to the status of an existing bake"
    httpServer.expect("POST", bakePath).andRespond().withStatus(HTTP_SEE_OTHER).withHeader(LOCATION, "$statusURI/$statusId")
    httpServer.expect("GET", "$statusPath/$statusId").andRespond().withStatus(HTTP_OK).withJsonContent {
      state "RUNNING"
      progress 1
      resource_uri "$bakeURI/$bakeId"
      uri "$statusURI/$statusId"
      id statusId
      attempts 1
      ctime 1382310109766
      mtime 1382310109766
      messages(["on instance i-66f5913d runnning: aminate ..."])
    }

    expect: "createBake should return the status of the bake"
    with(bakery.createBake(region, bake).toBlockingObservable().first()) {
      id == statusId
      state == BakeStatus.State.RUNNING
    }
  }

}
