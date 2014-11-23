package com.netflix.spinnaker.kato.aws
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials

class TestCredential {
    public static NetflixAssumeRoleAmazonCredentials named(String name, Map params = [:]) {
        def credJson = [
                name: 'name',
                accountId: "123456789012",
                defaultKeyPair: 'default-keypair',
                regions: [[name: 'us-east-1', availabilityZones: ['us-east-1c', 'us-east-1d']]],
                assumeRole: 'role/asgard',
                sessionName: 'Spinnaker'
        ] + params

        new ObjectMapper().convertValue(credJson, NetflixAssumeRoleAmazonCredentials)
    }
}
