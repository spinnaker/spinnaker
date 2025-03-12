/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google

import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient
import java.util.concurrent.TimeUnit


public class GoogleApiTestUtils {
  static public HttpResponseException makeHttpResponseException(int statusCode) {
    def errorMessage = "there was an error"
    def builder = new HttpResponseException.Builder(
        statusCode,
        "Injected Error",
        new HttpHeaders()).setMessage(statusCode.toString() + " Injected Error")
    return new HttpResponseException(builder)
  }

  static public Map makeTraitsTagMap(String method, int statusCode, Map extra) {
    String statusString = statusCode.toString()
    String series = statusString[0]
    // See note in GoogleExecutorTraitsSpec as to why 0 is success in tests
    String ok = (series == "2" || series == "0") ? "true" : "false"
    return [api: method, success: ok, statusCode: statusString, status: series + "xx"] + extra
  }

  static public Id makeOkId(Registry registry, String method, Map extra) {
    // See GoogleExecutorTraitsSpec as to why the statusCode is 0
    return makeId(registry, method, 0, extra)
  }

  static public Id makeId(Registry registry, String method, int statusCode, Map extra) {
    extra = ["account":  AccountForClient.UNKNOWN_ACCOUNT] + extra
    return registry.createId("google.api", makeTraitsTagMap(method, statusCode, extra))
  }

  /**
   * Creates a timer, records a 0 time on it, and returns the id.
   * This is so that tests can check the timer for a count
   */
  static public Id seedTimer(Registry registry, String method, Map extra) {
    Id id = makeOkId(registry, method, extra)
    print "INITIAL " + registry.timer(id).count()
    print "BEFORE " + registry.timer(id).count()
    registry.timer(id).record(0, TimeUnit.NANOSECONDS)
    print "FINAL " + registry.timer(id).count()
  }

}
