package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

import static com.netflix.spinnaker.clouddriver.ecs.provider.view.EcrImageProvider.ECR_REPOSITORY_URI_PATTERN;

/**
 * This ImageRepositoryProvider does not validate that the image does indeed exist.  An invalid image URL will lead to
 * the ECS Agent to fail at starting ECS tasks for the deployed server group, and is likely to be painful
 * to track and fix for users.  Still, this class allows to decouple the ECS cloud provider from the ECR docker registry.
 */
@Component
public class UnvalidatedDockerImageProvider implements ImageRepositoryProvider {

  @Override
  public String getRepositoryName() {
    return "Unvalidated";
  }

  @Override
  public boolean handles(String url) {
    return !isAnEcrUrl(url);
  }

  private boolean isAnEcrUrl(String url) {
    Matcher matcher = ECR_REPOSITORY_URI_PATTERN.matcher(url);
    return matcher.find();
  }

  @Override
  public List<EcsDockerImage> findImage(String url) {
    EcsDockerImage ecsDockerImage = new EcsDockerImage();
    ecsDockerImage.setImageName(url);

    return Collections.singletonList(ecsDockerImage);
  }
}
