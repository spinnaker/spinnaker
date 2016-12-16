/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.model

import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineModelUtil
import spock.lang.Specification

class AppEngineModelUtilSpec extends Specification {
  void "url builder properly builds service urls from selfLink"() {
    when:
      def selfLink = "apps/myapp/services/myservice"

    then:
     "myservice-dot-myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, "-dot-")
     "myservice.myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, ".")
  }

  void "url builder properly builds service urls from selfLink for default service"() {
    when:
      def selfLink = "apps/myapp/services/default"

    then:
      "myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, "-dot-")
      "myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, ".")
  }

  void "url builder properly builds version urls from selfLink"() {
    when:
      def selfLink = "apps/myapp/services/myservice/versions/myversion"

    then:
      "myversion-dot-myservice-dot-myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, "-dot-")
      "myversion.myservice.myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, ".")
  }

  void "url builder properly builds version urls from selfLink for version in default service"() {
    when:
      def selfLink = "apps/myapp/services/default/versions/myversion"

    then:
      "myversion-dot-myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, "-dot-")
      "myversion.myapp.appspot.com" == AppEngineModelUtil.getUrl(selfLink, ".")
  }
}
