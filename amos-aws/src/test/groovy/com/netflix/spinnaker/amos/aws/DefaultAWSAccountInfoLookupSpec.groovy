package com.netflix.spinnaker.amos.aws

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import spock.lang.Specification

class DefaultAWSAccountInfoLookupSpec extends Specification {

    def 'it should regex the error'() {

        def cred = Mock(AWSCredentials)
        def creds = Stub(AWSCredentialsProvider) {
            getCredentials() >> cred
        }
        String errMsg = 'com.amazonaws.AmazonServiceException: User: arn:aws:sts::149510111645:assumed-role/SpinnakerInstanceProfile/i-faea8732 is not authorized to perform: iam:GetUser on resource: arn:aws:sts::149510111645:assumed-role/SpinnakerInstanceProfile/i-faea8732 (Service: AmazonIdentityManagement; Status Code: 403; Error Code: AccessDenied; Request ID: bcd9f5c2-63a2-11e4-947e-d5b6d530e261)'
        def exception = new AmazonServiceException(errMsg)
        exception.setErrorCode('AccessDenied')

        def lookup = new DefaultAWSAccountInfoLookup(creds)

        when:
        long actId = lookup.findAccountId()

        then:
        cred._ >> { throw exception }
        actId == 149510111645

    }
}