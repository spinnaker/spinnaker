package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

public class TitusDeploymentResult extends DeploymentResult {

  @Getter private String titusAccountId;
  @Getter private String jobUri;

  public static TitusDeploymentResult from(
      TitusDeployDescription description,
      TitusJobSubmitted event,
      List<String> messages,
      String titusAccountId) {
    TitusDeploymentResult result = new TitusDeploymentResult();

    if (JobType.isEqual(description.getJobType(), JobType.SERVICE)) {
      forServiceJob(result, event.getServerGroupNameByRegion());
    } else {
      forBatchJob(result, description.getRegion(), event.getJobUri());
    }

    result.jobUri = event.getJobUri();
    result.titusAccountId = titusAccountId;
    result.setMessages(messages);

    return result;
  }

  /** Batch jobs use the "deployedNames" fields of the deployment result. */
  private static void forBatchJob(TitusDeploymentResult result, String region, String jobUri) {
    result.setDeployedNames(Collections.singletonList(jobUri));
    result.setDeployedNamesByLocation(
        Collections.singletonMap(region, Collections.singletonList(jobUri)));
  }

  /** Service jobs use the "serverGroupNames" fields for the deployment result. */
  private static void forServiceJob(
      TitusDeploymentResult result, Map<String, String> serverGroupNameByRegion) {
    result.setServerGroupNames(
        serverGroupNameByRegion.entrySet().stream()
            .map(e -> format("%s:%s", e.getKey(), e.getValue()))
            .collect(Collectors.toList()));
    result.setServerGroupNameByRegion(serverGroupNameByRegion);
  }
}
