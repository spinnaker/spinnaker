'use strict';

const angular = require('angular');

import { CloudProviderRegistry } from '@spinnaker/core';
import { DCOS_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import './help/dcos.help';

require('./logo/dcos.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.dcos', [
    DCOS_KEY_VALUE_DETAILS,
    require('./instance/details/details.dcos.module').name,
    require('./loadBalancer/configure/configure.dcos.module').name,
    require('./loadBalancer/details/details.dcos.module').name,
    require('./loadBalancer/transformer').name,
    require('./pipeline/stages/destroyAsg/dcosDestroyAsgStage').name,
    require('./pipeline/stages/disableAsg/dcosDisableAsgStage').name,
    require('./pipeline/stages/disableCluster/dcosDisableClusterStage').name,
    require('./pipeline/stages/findAmi/dcosFindAmiStage').name,
    require('./pipeline/stages/resizeAsg/dcosResizeAsgStage').name,
    require('./pipeline/stages/runJob/runJobStage').name,
    require('./pipeline/stages/scaleDownCluster/dcosScaleDownClusterStage').name,
    require('./pipeline/stages/shrinkCluster/dcosShrinkClusterStage').name,
    require('./proxy/ui.service').name,
    require('./serverGroup/configure/CommandBuilder').name,
    require('./serverGroup/configure/configure.dcos.module').name,
    require('./serverGroup/details/details.dcos.module').name,
    require('./serverGroup/transformer').name,
    require('./validation/applicationName.validator').name,
    require('./common/selectField.directive').name,
  ])
  .config(function() {
    CloudProviderRegistry.registerProvider('dcos', {
      name: 'DC/OS',
      logo: {
        path: require('./logo/dcos.logo.png'),
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/details.html'),
        detailsController: 'dcosInstanceDetailsController',
      },
      loadBalancer: {
        transformer: 'dcosLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/details.html'),
        detailsController: 'dcosLoadBalancerDetailsController',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
        createLoadBalancerController: 'dcosUpsertLoadBalancerController',
      },
      image: {
        reader: 'dcosImageReader',
      },
      serverGroup: {
        skipUpstreamStageCheck: true,
        transformer: 'dcosServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        detailsController: 'dcosServerGroupDetailsController',
        cloneServerGroupController: 'dcosCloneServerGroupController',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
        commandBuilder: 'dcosServerGroupCommandBuilder',
        configurationService: 'dcosServerGroupConfigurationService',
      },
    });
  });
