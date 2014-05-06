package com.netflix.kato.deploy.aws.description

class CreateAmazonLoadBalancerDescription extends AbstractAmazonCredentialsDescription {
  String clusterName
  Map<String, List<String>> availabilityZones
  List<Listener> listeners

  static class Listener {
    enum ListenerType {
      HTTP, HTTPS, TCP, SSL
    }

    ListenerType externalProtocol
    ListenerType internalProtocol

    Integer externalPort
    Integer internalPort

    String sslCertificateId
  }
}
