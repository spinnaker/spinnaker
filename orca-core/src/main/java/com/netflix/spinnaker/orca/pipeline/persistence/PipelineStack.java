package com.netflix.spinnaker.orca.pipeline.persistence;

import java.util.List;

public interface PipelineStack {
  void add(String id, String content);

  void remove(String id, String content);

  boolean contains(String id);

  List<String> elements(String id);

  boolean addToListIfKeyExists(String id1, String id2, String content);
}
