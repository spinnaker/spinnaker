package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider.AwsLifecycleHook;
import java.util.Optional;

public class AwsLifecycleHookUtil {

  public static Optional<AwsLifecycleHook> getLifecycleHook(
      String transition,
      String defaultResult,
      String notificationTargetArn,
      String roleArn,
      Integer heartbeatTimeoutSeconds) {

    if (isBlank(notificationTargetArn) || isBlank(roleArn) || isBlank(defaultResult)) {
      return Optional.empty();
    }

    if (!notificationTargetArn.contains(":")) {
      notificationTargetArn = "arn:aws:sns:{{region}}:{{accountId}}:" + notificationTargetArn;
    }

    if (!roleArn.contains(":")) {
      roleArn = "arn:aws:iam::{{accountId}}:" + roleArn;
    }

    return Optional.of(
        new AwsLifecycleHook()
            .setLifecycleTransition(transition)
            .setDefaultResult(defaultResult.trim())
            .setHeartbeatTimeout(heartbeatTimeoutSeconds)
            .setNotificationTargetARN(notificationTargetArn.trim())
            .setRoleARN(roleArn.trim()));
  }
}
