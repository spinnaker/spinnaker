package com.netflix.oort.remoting

public interface RemoteResource {
  Map get(String uri)
  List query(String uri)
}