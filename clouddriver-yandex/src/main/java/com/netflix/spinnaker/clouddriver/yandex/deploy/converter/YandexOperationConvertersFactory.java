/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.deploy.converter;

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.yandex.YandexOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DeleteYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DestroyYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.EnableDisableYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.RebootYandexInstancesDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.ResizeYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexImageTagsDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.CloneYandexServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.DeleteYandexLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.DestroyYandexServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.DisableYandexServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.EnableYandexServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.ModifyYandexInstanceGroupOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.RebootYandexInstancesAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.ResizeYandexServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.UpsertYandexImageTagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.UpsertYandexLoadBalancerAtomicOperation;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Populates Yandex Atomic Operations Converters
 *
 * <p>The idea was to create factory class with annotated methods:
 *
 * <pre>{@code
 * @Bean(name = "upsertYandexLoadBalancerAtomicOperationConverter")
 * @YandexOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
 * public OperationConverter<
 *     UpsertYandexLoadBalancerDescription, UpsertYandexLoadBalancerAtomicOperation> upsertLoadBalancer() {
 *   return new OperationConverter<>(
 *       UpsertYandexLoadBalancerAtomicOperation::new, UpsertYandexLoadBalancerDescription.class);
 * }
 * }</pre>
 *
 * Could be implemented after a couple of fixes in AnnotationsBasedAtomicOperationsRegistry:
 *
 * <pre>{@code
 * String descriptionName = value.getClass().getAnnotation(providerAnnotationType).value();
 * VersionedDescription converterVersion = VersionedDescription.from(descriptionName);
 * }</pre>
 *
 * and process <code>Component</code>'s and <code>Bean</code>'s and in FeaturesController.
 */
@Configuration
public class YandexOperationConvertersFactory {
  @Component("cloneYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.CLONE_SERVER_GROUP)
  public static class CloneServerGroup
      extends OperationConverter<
          YandexInstanceGroupDescription, CloneYandexServerGroupAtomicOperation> {
    public CloneServerGroup() {
      super(CloneYandexServerGroupAtomicOperation::new, YandexInstanceGroupDescription.class);
    }
  }

  @Component("createYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.CREATE_SERVER_GROUP)
  public static class CreateServerGroup
      extends OperationConverter<YandexInstanceGroupDescription, DeployAtomicOperation> {
    public CreateServerGroup() {
      super(DeployAtomicOperation::new, YandexInstanceGroupDescription.class);
    }
  }

  @Component("deleteYandexLoadBalancerAtomicOperationConverter")
  @YandexOperation(AtomicOperations.DELETE_LOAD_BALANCER)
  public static class DeleteLoadBalancer
      extends OperationConverter<
          DeleteYandexLoadBalancerDescription, DeleteYandexLoadBalancerAtomicOperation> {
    public DeleteLoadBalancer() {
      super(
          DeleteYandexLoadBalancerAtomicOperation::new, DeleteYandexLoadBalancerDescription.class);
    }
  }

  @Component("destroyYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.DESTROY_SERVER_GROUP)
  public static class DestroyServerGroup
      extends OperationConverter<
          DestroyYandexServerGroupDescription, DestroyYandexServerGroupAtomicOperation> {
    public DestroyServerGroup() {
      super(
          DestroyYandexServerGroupAtomicOperation::new, DestroyYandexServerGroupDescription.class);
    }
  }

  @Component("disableYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.DISABLE_SERVER_GROUP)
  public static class DisableServerGroup
      extends OperationConverter<
          EnableDisableYandexServerGroupDescription, DisableYandexServerGroupAtomicOperation> {
    public DisableServerGroup() {
      super(
          DisableYandexServerGroupAtomicOperation::new,
          EnableDisableYandexServerGroupDescription.class);
    }
  }

  @Component("enableYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.ENABLE_SERVER_GROUP)
  public static class EnableServerGroup
      extends OperationConverter<
          EnableDisableYandexServerGroupDescription, EnableYandexServerGroupAtomicOperation> {
    public EnableServerGroup() {
      super(
          EnableYandexServerGroupAtomicOperation::new,
          EnableDisableYandexServerGroupDescription.class);
    }
  }

  @Component("rebootYandexInstancesAtomicOperationConverter")
  @YandexOperation(AtomicOperations.REBOOT_INSTANCES)
  public static class RebootInstances
      extends OperationConverter<
          RebootYandexInstancesDescription, RebootYandexInstancesAtomicOperation> {
    public RebootInstances() {
      super(RebootYandexInstancesAtomicOperation::new, RebootYandexInstancesDescription.class);
    }
  }

  @Component("resizeYandexServerGroupAtomicOperationConverter")
  @YandexOperation(AtomicOperations.RESIZE_SERVER_GROUP)
  public static class ResizeServerGroup
      extends OperationConverter<
          ResizeYandexServerGroupDescription, ResizeYandexServerGroupAtomicOperation> {
    public ResizeServerGroup() {
      super(ResizeYandexServerGroupAtomicOperation::new, ResizeYandexServerGroupDescription.class);
    }
  }

  @Component("upsertYandexImageTagsAtomicOperationConverter")
  @YandexOperation(AtomicOperations.UPSERT_IMAGE_TAGS)
  public static class UpsertImageTags
      extends OperationConverter<
          UpsertYandexImageTagsDescription, UpsertYandexImageTagsAtomicOperation> {
    public UpsertImageTags() {
      super(UpsertYandexImageTagsAtomicOperation::new, UpsertYandexImageTagsDescription.class);
    }
  }

  @Component("upsertYandexLoadBalancerAtomicOperationConverter")
  @YandexOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
  public static class UpsertLoadBalancer
      extends OperationConverter<
          UpsertYandexLoadBalancerDescription, UpsertYandexLoadBalancerAtomicOperation> {
    public UpsertLoadBalancer() {
      super(
          UpsertYandexLoadBalancerAtomicOperation::new, UpsertYandexLoadBalancerDescription.class);
    }
  }

  @Component("yandexModifyInstanceGroupOperationConverter")
  @YandexOperation(AtomicOperations.UPDATE_LAUNCH_CONFIG)
  public static class ModifyInstanceGroup
      extends OperationConverter<
          YandexInstanceGroupDescription, ModifyYandexInstanceGroupOperation> {
    public ModifyInstanceGroup() {
      super(ModifyYandexInstanceGroupOperation::new, YandexInstanceGroupDescription.class);
    }
  }
}
