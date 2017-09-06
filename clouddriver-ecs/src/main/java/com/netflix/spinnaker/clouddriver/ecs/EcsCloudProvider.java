package com.netflix.spinnaker.clouddriver.ecs;

import org.springframework.stereotype.Component;
import com.netflix.spinnaker.clouddriver.core.CloudProvider;

import java.lang.annotation.Annotation;

@Component
public class EcsCloudProvider implements CloudProvider {

  public static final String ID = "ecs";

  final String id = ID;

  final String displayName = "Amazon-ECS";

  final Class<? extends Annotation> operationAnnotationType = EcsOperation.class;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public Class<? extends Annotation> getOperationAnnotationType() {
    return operationAnnotationType;
  }
}
