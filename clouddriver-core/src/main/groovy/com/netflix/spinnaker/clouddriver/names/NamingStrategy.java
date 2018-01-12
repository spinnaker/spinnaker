package com.netflix.spinnaker.clouddriver.names;

import com.netflix.spinnaker.moniker.Namer;

public interface NamingStrategy<T> extends Namer<T> {
  String getName();
}
