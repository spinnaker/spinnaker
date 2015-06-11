package com.netflix.spinnaker.kato.aws.security

class BastionProperties {
    Boolean enabled
    String host
    String user
    Integer port
    String proxyCluster
    String proxyRegion
    String accountIamRole
}
