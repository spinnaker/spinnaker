import { module } from 'angular';

import {
  CloudProviderRegistry,
  SETTINGS,
  STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT,
  YAML_EDITOR_COMPONENT,
} from '@spinnaker/core';

import './help/kubernetes.help';
import { KUBERNETES_INSTANCE_DETAILS_CTRL } from './instance/details/details.controller';
import { KUBERNETES_LOAD_BALANCER_DETAILS_CTRL } from './loadBalancer/details/details.controller';
import { KUBERNETES_LOAD_BALANCER_TRANSFORMER } from './loadBalancer/transformer';
import kubernetesLogo from './logo/kubernetes.logo.svg';
import { KUBERNETES_ANNOTATION_CUSTOM_SECTIONS } from './manifest/annotationCustomSections.component';
import { KUBERNETES_MANIFEST_ARTIFACT } from './manifest/artifact/artifact.component';
import { KUBERNETES_MANIFEST_DELETE_CTRL } from './manifest/delete/delete.controller';
import { JSON_EDITOR_COMPONENT } from './manifest/editor/json/jsonEditor.component';
import { KUBERNETES_MANIFEST_EVENTS } from './manifest/manifestEvents.component';
import { KUBERNETES_MANIFEST_IMAGE_DETAILS } from './manifest/manifestImageDetails.component';
import { KUBERNETES_MANIFEST_LABELS } from './manifest/manifestLabels.component';
import { KUBERNETES_MANIFEST_QOS } from './manifest/manifestQos.component';
import { KUBERNETES_MANIFEST_RESOURCES } from './manifest/manifestResources.component';
import { KUBERNETES_ROLLING_RESTART } from './manifest/rollout/RollingRestart';
import { KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL } from './manifest/rollout/pause.controller';
import { KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL } from './manifest/rollout/resume.controller';
import { KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL } from './manifest/rollout/undo.controller';
import { KUBERNETES_MANIFEST_SCALE_CTRL } from './manifest/scale/scale.controller';
import { KUBERNETES_MANIFEST_SELECTOR } from './manifest/selector/selector.component';
import { KUBERNETES_MANIFEST_CONDITION } from './manifest/status/condition.component';
import { KUBERNETES_MANIFEST_STATUS } from './manifest/status/status.component';
import { ManifestWizard } from './manifest/wizard/ManifestWizard';
import './pipelines/stages';
import { KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE } from './pipelines/stages/findArtifactsFromResource/findArtifactsFromResourceStage';
import { KUBERNETES_SCALE_MANIFEST_STAGE } from './pipelines/stages/scaleManifest/scaleManifestStage';
import { KUBERNETES_DISABLE_MANIFEST_STAGE } from './pipelines/stages/traffic/disableManifest.stage';
import { KUBERNETES_ENABLE_MANIFEST_STAGE } from './pipelines/stages/traffic/enableManifest.stage';
import { KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE } from './pipelines/stages/undoRolloutManifest/undoRolloutManifestStage';
import './pipelines/validation/manifestSelector.validator';
import { KUBERNETS_RAW_RESOURCE_MODULE } from './rawResource';
import { KUBERNETES_RESOURCE_STATES } from './resources/resources.state';
import { KUBERNETES_SECURITY_GROUP_DETAILS_CTRL } from './securityGroup/details/details.controller';
import { KubernetesSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { KUBERNETES_SECURITY_GROUP_TRANSFORMER } from './securityGroup/transformer';
import { KUBERNETES_SERVER_GROUP_DETAILS_CTRL } from './serverGroup/details/details.controller';
import { KUBERNETES_SERVER_GROUP_RESIZE_CTRL } from './serverGroup/details/resize/resize.controller';
import { KUBERNETES_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/serverGroupCommandBuilder.service';
import { KUBERNETES_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroupTransformer.service';
import { KUBERNETES_SERVER_GROUP_MANAGER_DETAILS_CTRL } from './serverGroupManager/details/details.controller';
import './validation/applicationName.validator';

import './logo/kubernetes.logo.less';

export const KUBERNETES_MODULE = 'spinnaker.kubernetes';

const requires = [
  KUBERNETES_INSTANCE_DETAILS_CTRL,
  KUBERNETES_LOAD_BALANCER_DETAILS_CTRL,
  KUBERNETES_SECURITY_GROUP_DETAILS_CTRL,
  KUBERNETES_SERVER_GROUP_COMMAND_BUILDER,
  KUBERNETES_SERVER_GROUP_DETAILS_CTRL,
  KUBERNETES_SERVER_GROUP_TRANSFORMER,
  KUBERNETES_SERVER_GROUP_MANAGER_DETAILS_CTRL,
  KUBERNETES_SERVER_GROUP_RESIZE_CTRL,
  KUBERNETES_SERVER_GROUP_MANAGER_DETAILS_CTRL,
  KUBERNETES_MANIFEST_DELETE_CTRL,
  KUBERNETES_MANIFEST_SCALE_CTRL,
  KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_STATUS,
  KUBERNETES_MANIFEST_CONDITION,
  KUBERNETES_MANIFEST_ARTIFACT,
  KUBERNETES_LOAD_BALANCER_TRANSFORMER,
  KUBERNETES_SECURITY_GROUP_TRANSFORMER,
  KUBERNETES_SCALE_MANIFEST_STAGE,
  KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE,
  KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE,
  KUBERNETES_MANIFEST_SELECTOR,
  KUBERNETES_MANIFEST_LABELS,
  KUBERNETES_MANIFEST_EVENTS,
  KUBERNETES_MANIFEST_RESOURCES,
  KUBERNETES_MANIFEST_QOS,
  KUBERNETES_ANNOTATION_CUSTOM_SECTIONS,
  KUBERNETES_MANIFEST_IMAGE_DETAILS,
  KUBERNETES_RESOURCE_STATES,
  YAML_EDITOR_COMPONENT,
  JSON_EDITOR_COMPONENT,
  KUBERNETES_ENABLE_MANIFEST_STAGE,
  KUBERNETES_DISABLE_MANIFEST_STAGE,
  STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT,
  KUBERNETES_ROLLING_RESTART,
];

if (SETTINGS.feature.kubernetesRawResources) {
  requires.push(KUBERNETS_RAW_RESOURCE_MODULE);
}

module(KUBERNETES_MODULE, requires).config(() => {
  CloudProviderRegistry.registerProvider('kubernetes', {
    name: 'Kubernetes',
    kubernetesAdHocInfraWritesEnabled: SETTINGS.kubernetesAdHocInfraWritesEnabled,
    logo: {
      path: kubernetesLogo,
    },
    serverGroup: {
      CloneServerGroupModal: ManifestWizard,
      commandBuilder: 'kubernetesV2ServerGroupCommandBuilder',
      detailsController: 'kubernetesV2ServerGroupDetailsCtrl',
      detailsTemplateUrl: require('./serverGroup/details/details.html'),
      transformer: 'kubernetesV2ServerGroupTransformer',
    },
    serverGroupManager: {
      detailsTemplateUrl: require('./serverGroupManager/details/details.html'),
      detailsController: 'kubernetesV2ServerGroupManagerDetailsCtrl',
    },
    loadBalancer: {
      CreateLoadBalancerModal: ManifestWizard,
      detailsController: 'kubernetesV2LoadBalancerDetailsCtrl',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      transformer: 'kubernetesV2LoadBalancerTransformer',
    },
    securityGroup: {
      reader: KubernetesSecurityGroupReader,
      CreateSecurityGroupModal: ManifestWizard,
      detailsController: 'kubernetesV2SecurityGroupDetailsCtrl',
      detailsTemplateUrl: require('./securityGroup/details/details.html'),
      transformer: 'kubernetesV2SecurityGroupTransformer',
    },
    instance: {
      detailsController: 'kubernetesV2InstanceDetailsCtrl',
      detailsTemplateUrl: require('./instance/details/details.html'),
    },
    unsupportedStageTypes: [
      'deploy',
      'destroyServerGroup',
      'disableCluster',
      'disableServerGroup',
      'enableServerGroup',
      'findImage',
      'resizeServerGroup',
      'rollbackCluster',
      'runJob',
      'scaleDown',
      'scaleDownCluster',
      'shrinkCluster',
      'upsertLoadBalancers',
    ],
  });
});
