/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.utils

import org.jets3t.service.impl.rest.httpclient.RestS3Service
import org.jets3t.service.security.AWSCredentials
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

class DefaultS3ServiceFactorySpec extends Specification {

  void "handles null AWS credentials"() {
    given:
    def factory = new DefaultS3ServiceFactory()

    when:
    def s3 = factory.createS3Service(null)

    then:
    s3 instanceof RestS3Service
    ReflectionTestUtils.getField(s3, 'credentials') == null
  }

  void "handles populated AWS credentials"() {
    given:
    def accessKey = 'my-access-key'
    def secretKey = 'my-secret-key'
    def factory = new DefaultS3ServiceFactory()

    when:
    def s3 = factory.createS3Service(new AWSCredentials(accessKey, secretKey))

    then:
    AWSCredentials creds = (AWSCredentials) ReflectionTestUtils.getField(s3, 'credentials')
    creds.accessKey == accessKey
    creds.secretKey == secretKey
  }

}
