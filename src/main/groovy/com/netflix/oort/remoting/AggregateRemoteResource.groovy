package com.netflix.oort.remoting

class AggregateRemoteResource implements RemoteResource {

  final Map<String, RemoteResource> remoteResources

  AggregateRemoteResource(Map<String, RemoteResource> remoteResources) {
    this.remoteResources = remoteResources
  }

  @Override
  Map get(String uri) {
    def result = [:]
    remoteResources.values().each {
      result << it.get(uri)
    }
    result
  }

  @Override
  List query(String uri) {
    def results = []
    remoteResources.values().each {
      results.addAll it.query(uri)
    }
    results
  }

  RemoteResource getRemoteResource(String name) {
    remoteResources[name]
  }
}
