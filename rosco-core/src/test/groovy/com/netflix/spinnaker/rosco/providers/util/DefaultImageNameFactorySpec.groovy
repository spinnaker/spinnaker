/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.rosco.api.BakeRequest
import spock.lang.Specification

import java.time.Clock

class DefaultImageNameFactorySpec extends Specification {

  void "should recognize fully-qualified ubuntu package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-h12.170cdbd_all",
                                        base_os: BakeRequest.OperatingSystem.ubuntu)

    when:
      def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      appVersion == AppVersion.parseName(appVersionStr)
      packagesParameter == "nflx-djangobase-enhanced"
  }

  void "should recognize unqualified ubuntu package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "reno-server",
                                        base_os: BakeRequest.OperatingSystem.ubuntu)

    when:
      def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "reno-server-all-123456-ubuntu"
      appVersionStr == "reno-server"
      appVersion == null
      packagesParameter == "reno-server"
  }

  void "should recognize fully-qualified ubuntu package name plus extra packages"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-h12.170cdbd_all kato redis-server",
                                        base_os: BakeRequest.OperatingSystem.ubuntu)

    when:
      def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      appVersion == AppVersion.parseName(appVersionStr)
      packagesParameter == "nflx-djangobase-enhanced kato redis-server"
  }

  void "should recognize fully-qualified centos package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-h12.170cdbd-all",
                                        base_os: BakeRequest.OperatingSystem.centos)

    when:
      def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      appVersion == AppVersion.parseName(appVersionStr)
      packagesParameter == "nflx-djangobase-enhanced"
  }

  void "should recognize fully-qualified centos package name plus extra packages"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-h12.170cdbd-all kato redis-server",
                                        base_os: BakeRequest.OperatingSystem.centos)

    when:
      def (imageName, appVersionStr, appVersion, packagesParameter) = imageNameFactory.produceImageName(bakeRequest)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      appVersion == AppVersion.parseName(appVersionStr)
      packagesParameter == "nflx-djangobase-enhanced kato redis-server"
  }
}
