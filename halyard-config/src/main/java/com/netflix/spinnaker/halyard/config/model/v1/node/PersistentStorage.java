package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This is the configuration for S3/GCS storage options.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PersistentStorage extends Node {
  @Override
  public void accept(ProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public String getNodeName() {
    return "persistentStorage";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  private String accountName;
  private String bucket;
  private String rootFolder;
}
