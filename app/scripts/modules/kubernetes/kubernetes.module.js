'use strict';

let angular = require('angular');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.kubernetes', [
  require('./serverGroup/configure/CommandBuilder.js'),
  require('./serverGroup/configure/configure.kubernetes.module.js'),
  require('./serverGroup/details/details.kubernetes.module.js'),
  require('./serverGroup/transformer.js'),
  require('./instance/details/details.kubernetes.module.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      name: 'Kubernetes',
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
