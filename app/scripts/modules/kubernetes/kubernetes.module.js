'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry } from '@spinnaker/core';

import { KUBERNETES_ANNOTATION_CONFIGURER } from './annotation/annotationConfigurer.component';
import { KUBERNETES_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import { KUBERNETES_SECURITY_CONTEXT_SELECTOR } from './container/securityContext/securityContextSelector.component';
import { KUBERNETES_HELP } from './help/kubernetes.help';

import './logo/kubernetes.logo.less';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.kubernetes', [
  require('./autoscaler/autoscaler.write.service.js'),
  require('./cache/configurer.service.js'),
  require('./cluster/cluster.kubernetes.module.js'),
  require('./container/configurer.directive.js'),
  require('./container/probe.directive.js'),
  require('./event/event.directive.js'),
  require('./instance/details/details.kubernetes.module.js'),
  CLOUD_PROVIDER_REGISTRY,
  KUBERNETES_ANNOTATION_CONFIGURER,
  KUBERNETES_KEY_VALUE_DETAILS,
  KUBERNETES_SECURITY_CONTEXT_SELECTOR,
  KUBERNETES_HELP,
  require('./loadBalancer/configure/configure.kubernetes.module.js'),
  require('./loadBalancer/details/details.kubernetes.module.js'),
  require('./loadBalancer/transformer.js'),
  require('./namespace/multiSelectField.component.js'),
  require('./namespace/selectField.directive.js'),
  require('./pipeline/stages/destroyAsg/kubernetesDestroyAsgStage.js'),
  require('./pipeline/stages/disableAsg/kubernetesDisableAsgStage.js'),
  require('./pipeline/stages/disableCluster/kubernetesDisableClusterStage.js'),
  require('./pipeline/stages/enableAsg/kubernetesEnableAsgStage.js'),
  require('./pipeline/stages/findAmi/kubernetesFindAmiStage.js'),
  require('./pipeline/stages/resizeAsg/resizeStage.js'),
  require('./pipeline/stages/runJob/runJobStage.js'),
  require('./pipeline/stages/scaleDownCluster/scaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/shrinkClusterStage.js'),
  require('./proxy/ui.service.js'),
  require('./search/resultFormatter.js'),
  require('./securityGroup/configure/configure.kubernetes.module.js'),
  require('./securityGroup/details/details.kubernetes.module.js'),
  require('./securityGroup/reader.js'),
  require('./securityGroup/transformer.js'),
  require('./serverGroup/configure/CommandBuilder.js'),
  require('./serverGroup/configure/configure.kubernetes.module.js'),
  require('./serverGroup/details/details.kubernetes.module.js'),
  require('./serverGroup/paramsMixin.js'),
  require('./serverGroup/transformer.js'),
  require('./validation/applicationName.validator.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      name: 'Kubernetes',
      cache: {
        configurer: 'kubernetesCacheConfigurer',
      },
      search: {
        resultFormatter: 'kubernetesSearchResultFormatter',
      },
      logo: {
        path: require('./logo/kubernetes.logo.png'),
      },
      image: {
        reader: 'kubernetesImageReader',
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/details.html'),
        detailsController: 'kubernetesInstanceDetailsController',
      },
      loadBalancer: {
        transformer: 'kubernetesLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/details.html'),
        detailsController: 'kubernetesLoadBalancerDetailsController',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
        createLoadBalancerController: 'kubernetesUpsertLoadBalancerController',
      },
      securityGroup: {
        reader: 'kubernetesSecurityGroupReader',
        transformer: 'kubernetesSecurityGroupTransformer',
        detailsTemplateUrl: require('./securityGroup/details/details.html'),
        detailsController: 'kubernetesSecurityGroupDetailsController',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/wizard/createWizard.html'),
        createSecurityGroupController: 'kubernetesUpsertSecurityGroupController',
      },
      serverGroup: {
        skipUpstreamStageCheck: true,
        transformer: 'kubernetesServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        detailsController: 'kubernetesServerGroupDetailsController',
        cloneServerGroupController: 'kubernetesCloneServerGroupController',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
        commandBuilder: 'kubernetesServerGroupCommandBuilder',
        configurationService: 'kubernetesServerGroupConfigurationService',
        paramsMixin: 'kubernetesServerGroupParamsMixin',
      },
    });
  });

DeploymentStrategyRegistry.registerProvider('kubernetes', ['custom', 'redblack']);
