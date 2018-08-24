import { module } from 'angular';

import { CloudProviderRegistry } from '@spinnaker/core';

import '../logo/kubernetes.logo.less';
import { KUBERNETES_MANIFEST_BASIC_SETTINGS } from './manifest/wizard/basicSettings.component';
import { KUBERNETES_MANIFEST_CTRL } from './manifest/wizard/manifestWizard.controller';
import { KUBERNETES_EDIT_MANIFEST_CTRL } from './manifest/edit/editManifestWizard.controller';
import { KUBERNETES_MANIFEST_DELETE_CTRL } from './manifest/delete/delete.controller';
import { KUBERNETES_MANIFEST_SCALE_CTRL } from './manifest/scale/scale.controller';
import { KUBERNETES_MANIFEST_ENTRY } from './manifest/wizard/manifestEntry.component';
import { KUBERNETES_V2_INSTANCE_DETAILS_CTRL } from './instance/details/details.controller';
import { KUBERNETES_DEPLOY_MANIFEST_STAGE } from './pipelines/stages/deployManifest/deployManifestStage';
import { KUBERNETES_DELETE_MANIFEST_STAGE } from './pipelines/stages/deleteManifest/deleteManifestStage';
import { KUBERNETES_PATCH_MANIFEST_STAGE } from './pipelines/stages/patchManifest/patchManifestStage';
import { KUBERNETES_SCALE_MANIFEST_STAGE } from './pipelines/stages/scaleManifest/scaleManifestStage';
import { KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE } from './pipelines/stages/undoRolloutManifest/undoRolloutManifestStage';
import { KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE } from './pipelines/stages/findArtifactsFromResource/findArtifactsFromResourceStage';
import { KUBERNETES_V2_LOAD_BALANCER_DETAILS_CTRL } from './loadBalancer/details/details.controller';
import { KUBERNETES_V2_SECURITY_GROUP_DETAILS_CTRL } from './securityGroup/details/details.controller';
import { KUBERNETES_V2_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroupTransformer.service';
import { KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL } from './serverGroup/details/details.controller';
import { KUBERNETES_V2_SERVER_GROUP_RESIZE_CTRL } from './serverGroup/details/resize/resize.controller';
import { KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/serverGroupCommandBuilder.service';
import { KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL } from './serverGroupManager/details/details.controller';
import { KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL } from './manifest/rollout/undo.controller';
import { KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL } from './manifest/rollout/pause.controller';
import { KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL } from './manifest/rollout/resume.controller';
import { KUBERNETES_MANIFEST_STATUS } from './manifest/status/status.component';
import { KUBERNETES_MANIFEST_CONDITION } from './manifest/status/condition.component';
import { KUBERNETES_MANIFEST_ARTIFACT } from './manifest/artifact/artifact.component';
import { KUBERNETES_MANIFEST_SELECTOR } from './manifest/selector/selector.component';
import { KUBERNETES_MANIFEST_LABELS } from './manifest/manifestLabels.component';
import { KUBERNETES_MANIFEST_EVENTS } from './manifest/manifestEvents.component';
import { KUBERNETES_MANIFEST_RESOURCES } from './manifest/manifestResources.component';
import { KUBERNETES_MANIFEST_QOS } from './manifest/manifestQos.component';
import { KUBERNETES_MULTI_MANIFEST_SELECTOR } from './manifest/selector/multiSelector.component';
import { KUBERNETES_V2_LOAD_BALANCER_TRANSFORMER } from './loadBalancer/transformer';
import { KUBERNETES_V2_SECURITY_GROUP_TRANSFORMER } from './securityGroup/transformer';
import { KUBERNETES_ANNOTATION_CUSTOM_SECTIONS } from './manifest/annotationCustomSections.component';
import { KUBERNETES_V2_RESOURCE_STATES } from './resources/resources.state';
import { YAML_EDITOR_COMPONENT } from './manifest/yaml/yamlEditor.component';

// load all templates into the $templateCache
const templates = require.context('kubernetes', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const KUBERNETES_V2_MODULE = 'spinnaker.kubernetes.v2';

module(KUBERNETES_V2_MODULE, [
  KUBERNETES_V2_INSTANCE_DETAILS_CTRL,
  KUBERNETES_V2_LOAD_BALANCER_DETAILS_CTRL,
  KUBERNETES_V2_SECURITY_GROUP_DETAILS_CTRL,
  KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER,
  KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL,
  KUBERNETES_V2_SERVER_GROUP_TRANSFORMER,
  KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL,
  KUBERNETES_V2_SERVER_GROUP_RESIZE_CTRL,
  KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL,
  KUBERNETES_MANIFEST_BASIC_SETTINGS,
  KUBERNETES_MANIFEST_CTRL,
  KUBERNETES_EDIT_MANIFEST_CTRL,
  KUBERNETES_MANIFEST_DELETE_CTRL,
  KUBERNETES_MANIFEST_SCALE_CTRL,
  KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL,
  KUBERNETES_MANIFEST_ENTRY,
  KUBERNETES_MANIFEST_STATUS,
  KUBERNETES_MANIFEST_CONDITION,
  KUBERNETES_MANIFEST_ARTIFACT,
  KUBERNETES_V2_LOAD_BALANCER_TRANSFORMER,
  KUBERNETES_V2_SECURITY_GROUP_TRANSFORMER,
  require('../securityGroup/reader.js').name,
  KUBERNETES_DEPLOY_MANIFEST_STAGE,
  KUBERNETES_DELETE_MANIFEST_STAGE,
  KUBERNETES_PATCH_MANIFEST_STAGE,
  KUBERNETES_SCALE_MANIFEST_STAGE,
  KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE,
  KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE,
  KUBERNETES_MANIFEST_SELECTOR,
  KUBERNETES_MULTI_MANIFEST_SELECTOR,
  KUBERNETES_MANIFEST_LABELS,
  KUBERNETES_MANIFEST_EVENTS,
  KUBERNETES_MANIFEST_RESOURCES,
  KUBERNETES_MANIFEST_QOS,
  KUBERNETES_ANNOTATION_CUSTOM_SECTIONS,
  KUBERNETES_V2_RESOURCE_STATES,
  YAML_EDITOR_COMPONENT,
]).config(() => {
  CloudProviderRegistry.registerProvider('kubernetes', {
    name: 'Kubernetes',
    skin: 'v2',
    logo: {
      path: require('../logo/kubernetes.icon.svg'),
    },
    serverGroup: {
      cloneServerGroupController: 'kubernetesManifestWizardCtrl',
      cloneServerGroupTemplateUrl: require('./manifest/wizard/manifestWizard.html'),
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
      createLoadBalancerController: 'kubernetesManifestWizardCtrl',
      createLoadBalancerTemplateUrl: require('./manifest/wizard/manifestWizard.html'),
      detailsController: 'kubernetesV2LoadBalancerDetailsCtrl',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      transformer: 'kubernetesV2LoadBalancerTransformer',
    },
    securityGroup: {
      reader: 'kubernetesSecurityGroupReader',
      createSecurityGroupController: 'kubernetesManifestWizardCtrl',
      createSecurityGroupTemplateUrl: require('./manifest/wizard/manifestWizard.html'),
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
    ],
  });
});
