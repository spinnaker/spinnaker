'use strict';

let angular = require('angular');

require('./logo/kubernetes.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.kubernetes', [
  require('../core/pipeline/config/stages/findAmi/kubernetes/kubernetesFindAmiStage.js'),
  require('../core/pipeline/config/stages/disableAsg/kubernetes/kubernetesDisableAsgStage.js'),
  require('../core/pipeline/config/stages/disableCluster/kubernetes/kubernetesDisableClusterStage.js'),
  require('../core/pipeline/config/stages/enableAsg/kubernetes/kubernetesEnableAsgStage.js'),
  require('../core/pipeline/config/stages/resizeAsg/kubernetes/resizeStage.js'),
  require('./cache/configurer.service.js'),
  require('./container/configurer.directive.js'),
  require('./container/probe.directive.js'),
  require('./instance/details/details.kubernetes.module.js'),
  require('./loadBalancer/configure/configure.kubernetes.module.js'),
  require('./loadBalancer/details/details.kubernetes.module.js'),
  require('./loadBalancer/transformer.js'),
  require('./namespace/multiSelectField.component.js'),
  require('./namespace/selectField.directive.js'),
  require('./search/resultFormatter.js'),
  require('./serverGroup/configure/CommandBuilder.js'),
  require('./serverGroup/configure/configure.kubernetes.module.js'),
  require('./serverGroup/details/details.kubernetes.module.js'),
  require('./serverGroup/transformer.js'),
  require('./validation/applicationName.validator.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      v2wizard: true,
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
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/wizard.html'),
        createLoadBalancerController: 'kubernetesUpsertLoadBalancerController',
      },
      serverGroup: {
        transformer: 'kubernetesServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        detailsController: 'kubernetesServerGroupDetailsController',
        cloneServerGroupController: 'kubernetesCloneServerGroupController',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
        commandBuilder: 'kubernetesServerGroupCommandBuilder',
        configurationService: 'kubernetesServerGroupConfigurationService',
      },
    });
  });
