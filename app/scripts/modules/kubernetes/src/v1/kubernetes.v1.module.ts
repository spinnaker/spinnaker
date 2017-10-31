import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { KUBERNETES_KEY_VALUE_DETAILS } from '../common/keyValueDetails.component';
import { KUBERNETES_SECURITY_CONTEXT_SELECTOR } from '../container/securityContext/securityContextSelector.component';
import { KUBERNETES_HELP } from '../help/kubernetes.help';

import '../logo/kubernetes.logo.less';

// load all templates into the $templateCache
const templates = require.context('kubernetes', true, /\.html$/);
templates.keys().forEach(function(key) {
    templates(key);
});

export const KUBERNETES_V1_MODULE = 'spinnaker.kubernetes.v1';
module(KUBERNETES_V1_MODULE, [
  require('../autoscaler/autoscaler.write.service.js').name,
  require('../cache/configurer.service.js').name,
  require('../cluster/cluster.kubernetes.module.js').name,
  require('../container/configurer.directive.js').name,
  require('../container/probe.directive.js').name,
  require('../event/event.directive.js').name,
  require('../instance/details/details.kubernetes.module.js').name,
  CLOUD_PROVIDER_REGISTRY,
  KUBERNETES_KEY_VALUE_DETAILS,
  KUBERNETES_SECURITY_CONTEXT_SELECTOR,
  KUBERNETES_HELP,
  require('../loadBalancer/configure/configure.kubernetes.module.js').name,
  require('../loadBalancer/details/details.kubernetes.module.js').name,
  require('../loadBalancer/transformer.js').name,
  require('../namespace/multiSelectField.component.js').name,
  require('../namespace/selectField.directive.js').name,
  require('../pipeline/stages/destroyAsg/kubernetesDestroyAsgStage.js').name,
  require('../pipeline/stages/disableAsg/kubernetesDisableAsgStage.js').name,
  require('../pipeline/stages/disableCluster/kubernetesDisableClusterStage.js').name,
  require('../pipeline/stages/enableAsg/kubernetesEnableAsgStage.js').name,
  require('../pipeline/stages/findAmi/kubernetesFindAmiStage.js').name,
  require('../pipeline/stages/resizeAsg/resizeStage.js').name,
  require('../pipeline/stages/runJob/runJobStage.js').name,
  require('../pipeline/stages/scaleDownCluster/scaleDownClusterStage.js').name,
  require('../pipeline/stages/shrinkCluster/shrinkClusterStage.js').name,
  require('../proxy/ui.service.js').name,
  require('../search/resultFormatter.js').name,
  require('../securityGroup/configure/configure.kubernetes.module.js').name,
  require('../securityGroup/details/details.kubernetes.module.js').name,
  require('../securityGroup/reader.js').name,
  require('../securityGroup/transformer.js').name,
  require('../serverGroup/configure/CommandBuilder.js').name,
  require('../serverGroup/configure/configure.kubernetes.module.js').name,
  require('../serverGroup/details/details.kubernetes.module.js').name,
  require('../serverGroup/paramsMixin.js').name,
  require('../serverGroup/transformer.js').name,
  require('../validation/applicationName.validator.js').name,
])
  .config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      name: 'Kubernetes',
      providerVersion: 'v1',
      defaultVersion: true,
      cache: {
        configurer: 'kubernetesCacheConfigurer',
      },
      search: {
        resultFormatter: 'kubernetesSearchResultFormatter',
      },
      logo: {
        path: require('../logo/kubernetes.logo.png'),
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
    });
  });

DeploymentStrategyRegistry.registerProvider('kubernetes', ['custom', 'redblack']);
