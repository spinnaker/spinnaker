package com.netflix.spinnaker.clouddriver.lambda.names;

import java.util.Map;

public interface LambdaResource {
  String getName();
  Map<String,String> getResourceTags();
  void setResourceTags(Map<String,String> tags);
}
