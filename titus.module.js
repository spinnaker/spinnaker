'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry } from '@spinnaker/core';

import { TITUS_MIGRATION_CONFIG_COMPONENT } from './migration/titusMigrationConfig.component';
import { TITUS_APPLICATION_NAME_VALIDATOR } from './validation/applicationName.validator';
import { TITUS_HELP } from './help/titus.help';

import './logo/titus.logo.less';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.titus', [
  CLOUD_PROVIDER_REGISTRY,
  require('./securityGroup/securityGroup.read.service'),
  require('./serverGroup/details/serverGroupDetails.titus.controller.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.titus.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.titus.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./instance/details/instance.details.controller.js'),
  TITUS_APPLICATION_NAME_VALIDATOR,
  TITUS_HELP,
  require('./pipeline/stages/findAmi/titusFindAmiStage.js'),
  require('./pipeline/stages/runJob/titusRunJobStage.js'),
  require('./pipeline/stages/enableAsg/titusEnableAsgStage.js'),
  require('./pipeline/stages/disableAsg/titusDisableAsgStage.js'),
  require('./pipeline/stages/destroyAsg/titusDestroyAsgStage.js'),
  require('./pipeline/stages/resizeAsg/titusResizeAsgStage.js'),
  require('./pipeline/stages/cloneServerGroup/titusCloneServerGroupStage.js'),
  require('./pipeline/stages/bake/titusBakeStage.js'),
  require('./pipeline/stages/disableCluster/titusDisableClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/titusShrinkClusterStage.js'),
  require('./pipeline/stages/scaleDownCluster/titusScaleDownClusterStage.js'),
  TITUS_MIGRATION_CONFIG_COMPONENT,
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('titus', {
      name: 'Titus',
      logo: {
        path: require('./logo/titus.logo.png')
      },
      serverGroup: {
        transformer: 'titusServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'titusServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'titusCloneServerGroupCtrl',
        commandBuilder: 'titusServerGroupCommandBuilder',
        configurationService: 'titusServerGroupConfigurationService',
        skipUpstreamStageCheck: true,
      },
      securityGroup: {
        reader: 'titusSecurityGroupReader',
        useProvider: 'aws'
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'titusInstanceDetailsCtrl'
      }
    });
  });

DeploymentStrategyRegistry.registerProvider('titus', ['custom', 'redblack']);
