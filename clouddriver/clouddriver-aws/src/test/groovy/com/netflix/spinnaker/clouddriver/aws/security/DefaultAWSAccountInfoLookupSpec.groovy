/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import spock.lang.Specification

class DefaultAWSAccountInfoLookupSpec extends Specification {

    def 'it should regex the error'() {

        def ec2 = Stub(AmazonEC2)
        def creds = Stub(AWSCredentialsProvider)
        def provider = Stub(AmazonClientProvider) {
            getAmazonEC2(creds, AmazonClientProvider.DEFAULT_REGION) >> ec2
        }
        String errMsg = 'com.amazonaws.AmazonServiceException: User: arn:aws:sts::123456:assumed-role/SpinnakerTestRole/i-fieber is not authorized to perform: iam:GetUser on resource: arn:aws:sts::123456:assumed-role/SpinnakerTestRole/i-fieber (Service: AmazonIdentityManagement; Status Code: 403; Error Code: AccessDenied; Request ID: bcd9f5c2-63a2-11e4-947e-d5b6d530e261)'
        def exception = new AmazonServiceException(errMsg)
        exception.setErrorCode('AccessDenied')

        def lookup = new DefaultAWSAccountInfoLookup(creds, provider)

        when:
        def actId = lookup.findAccountId()

        then:
        ec2._ >> { throw exception }
        actId == "123456"

    }
}
