package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is the configuration for S3/GCS storage options.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PersistentStorage extends Node {
  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
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

  protected List<String> accountNameOptions(ConfigProblemSetBuilder psBuilder) {
    DeploymentConfiguration context = parentOfType(DeploymentConfiguration.class);
    List<String> accounts = new ArrayList<>();
    GoogleProvider googleProvider = context.getProviders().getGoogle();

    if (googleProvider != null) {
      accounts.addAll(googleProvider
          .getAccounts()
          .stream()
          .map(Account::getName)
          .collect(Collectors.toList()));
    }

    return accounts;
  }

  private String accountName;
  private String bucket;
  private String rootFolder;
  private String location;
}
