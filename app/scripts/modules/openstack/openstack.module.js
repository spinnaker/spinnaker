'use strict';

let angular = require('angular');

require('./logo/openstack.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
_.forEach(templates.keys(), function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.openstack', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/serverGroup.configure.openstack.module.js'),
  require('./serverGroup/configure/wizard/Clone.controller.js'),
  require('./serverGroup/serverGroup.transformer.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('openstack', {
      name: 'Openstack',
      logo: {
        path: require('./logo/openstack.logo.png')
      },
      serverGroup: {
        transformer: 'openstackServerGroupTransformer',
        cloneServerGroupController: 'openstackCloneServerGroupCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        commandBuilder: 'openstackServerGroupCommandBuilder',
        configurationService: 'openstackServerGroupConfigurationService',
      }
    });
  });
