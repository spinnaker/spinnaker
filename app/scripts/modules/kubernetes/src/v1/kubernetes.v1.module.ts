import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { KUBERNETES_KEY_VALUE_DETAILS } from '../common/keyValueDetails.component';
import { KUBERNETES_TOLERATIONS } from '../common/tolerations/tolerations.component';
import { KUBERNETES_SECURITY_CONTEXT_SELECTOR } from '../container/securityContext/securityContextSelector.component';
import { KUBERNETES_SERVERGROUP_ARTIFACTEXTRACTOR } from '../serverGroup/artifactExtractor';
import '../help/kubernetes.help';
import { KubernetesProviderSettings } from '../kubernetes.settings';

import '../logo/kubernetes.logo.less';

// load all templates into the $templateCache
const templates = require.context('kubernetes', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const KUBERNETES_V1_MODULE = 'spinnaker.kubernetes.v1';
module(KUBERNETES_V1_MODULE, [
  require('../autoscaler/autoscaler.write.service').name,
  require('../cluster/cluster.kubernetes.module').name,
  require('../container/configurer.directive').name,
  require('../container/probe.directive').name,
  require('../event/event.directive').name,
  require('../instance/details/details.kubernetes.module').name,
  KUBERNETES_KEY_VALUE_DETAILS,
  KUBERNETES_SECURITY_CONTEXT_SELECTOR,
  require('../loadBalancer/configure/configure.kubernetes.module').name,
  require('../loadBalancer/details/details.kubernetes.module').name,
  require('../loadBalancer/transformer').name,
  require('../namespace/multiSelectField.component').name,
  require('../namespace/selectField.directive').name,
  require('../pipeline/stages/destroyAsg/kubernetesDestroyAsgStage').name,
  require('../pipeline/stages/disableAsg/kubernetesDisableAsgStage').name,
  require('../pipeline/stages/disableCluster/kubernetesDisableClusterStage').name,
  require('../pipeline/stages/enableAsg/kubernetesEnableAsgStage').name,
  require('../pipeline/stages/findAmi/kubernetesFindAmiStage').name,
  require('../pipeline/stages/resizeAsg/resizeStage').name,
  require('../pipeline/stages/runJob/runJobStage').name,
  require('../pipeline/stages/scaleDownCluster/scaleDownClusterStage').name,
  require('../pipeline/stages/shrinkCluster/shrinkClusterStage').name,
  require('../proxy/ui.service').name,
  require('../search/resultFormatter').name,
  require('../securityGroup/configure/configure.kubernetes.module').name,
  require('../securityGroup/details/details.kubernetes.module').name,
  require('../securityGroup/reader').name,
  require('../securityGroup/transformer').name,
  require('../serverGroup/configure/CommandBuilder').name,
  require('../serverGroup/configure/configure.kubernetes.module').name,
  require('../serverGroup/details/details.kubernetes.module').name,
  KUBERNETES_SERVERGROUP_ARTIFACTEXTRACTOR,
  require('../serverGroup/paramsMixin').name,
  require('../serverGroup/transformer').name,
  require('../validation/applicationName.validator').name,
  KUBERNETES_TOLERATIONS,
]).config(() => {
  CloudProviderRegistry.registerProvider('kubernetes', {
    name: 'Kubernetes',
    skin: 'v1',
    defaultSkin: true,
    search: {
      resultFormatter: 'kubernetesSearchResultFormatter',
    },
    logo: {
      path: require('../logo/kubernetes.logo.svg'),
    },
    image: {
      reader: 'kubernetesImageReader',
    },
    instance: {
      detailsTemplateUrl: require('../instance/details/details.html'),
      detailsController: 'kubernetesInstanceDetailsController',
    },
    loadBalancer: {
      transformer: 'kubernetesLoadBalancerTransformer',
      detailsTemplateUrl: require('../loadBalancer/details/details.html'),
      detailsController: 'kubernetesLoadBalancerDetailsController',
      createLoadBalancerTemplateUrl: require('../loadBalancer/configure/wizard/createWizard.html'),
      createLoadBalancerController: 'kubernetesUpsertLoadBalancerController',
    },
    securityGroup: {
      reader: 'kubernetesSecurityGroupReader',
      transformer: 'kubernetesSecurityGroupTransformer',
      detailsTemplateUrl: require('../securityGroup/details/details.html'),
      detailsController: 'kubernetesSecurityGroupDetailsController',
      createSecurityGroupTemplateUrl: require('../securityGroup/configure/wizard/createWizard.html'),
      createSecurityGroupController: 'kubernetesUpsertSecurityGroupController',
    },
    serverGroup: {
      artifactExtractor: 'kubernetesServerGroupArtifactExtractor',
      skipUpstreamStageCheck: true,
      transformer: 'kubernetesServerGroupTransformer',
      detailsTemplateUrl: require('../serverGroup/details/details.html'),
      detailsController: 'kubernetesServerGroupDetailsController',
      cloneServerGroupController: 'kubernetesCloneServerGroupController',
      cloneServerGroupTemplateUrl: require('../serverGroup/configure/wizard/wizard.html'),
      commandBuilder: 'kubernetesServerGroupCommandBuilder',
      configurationService: 'kubernetesServerGroupConfigurationService',
      paramsMixin: 'kubernetesServerGroupParamsMixin',
    },
    unsupportedStageTypes: [
      'scaleManifest',
      'deployManifest',
      'deleteManifest',
      'undoRolloutManifest',
      'findArtifactsFromResource',
      'bakeManifest',
      'patchManifest',
    ],
  });
});

const strategies = ['custom', 'redblack'];
if (KubernetesProviderSettings.defaults.rrb) {
  strategies.push('rollingredblack');
}

DeploymentStrategyRegistry.registerProvider('kubernetes', strategies);
