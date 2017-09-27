'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry } from '@spinnaker/core';

import './logo/cf.logo.less';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
    templates(key);
});

module.exports = angular.module('spinnaker.cf', [
    CLOUD_PROVIDER_REGISTRY,
    require('./instance/cfInstanceTypeService.js').name,
    require('./serverGroup/details/serverGroupDetails.cf.controller.js').name,
    require('./serverGroup/configure/ServerGroupCommandBuilder.js').name,
    require('./serverGroup/configure/wizard/CloneServerGroupCtrl.js').name,
    require('./serverGroup/configure/serverGroup.configure.cf.module.js').name,
    require('./serverGroup/serverGroup.transformer.js').name,
    require('./pipeline/stages/cloneServerGroup/cfCloneServerGroupStage.js').name,
    require('./pipeline/stages/destroyAsg/cfDestroyAsgStage.js').name,
    require('./pipeline/stages/disableAsg/cfDisableAsgStage.js').name,
    require('./pipeline/stages/enableAsg/cfEnableAsgStage.js').name,
    require('./pipeline/stages/findAmi/cfFindAmiStage.js').name,
    require('./pipeline/stages/resizeAsg/cfResizeAsgStage.js').name,
    require('./pipeline/stages/scaleDownCluster/cfScaleDownClusterStage.js').name,
    require('./pipeline/stages/shrinkCluster/cfShrinkClusterStage.js').name,
    require('./loadBalancer/loadBalancer.transformer.js').name,
    require('./loadBalancer/details/LoadBalancerDetailsCtrl.js').name,
    require('./loadBalancer/configure/CreateLoadBalancerCtrl.js').name,
    require('./instance/details/instance.details.controller.js').name,
    require('./securityGroup/details/SecurityGroupDetailsCtrl.js').name,
    require('./securityGroup/securityGroup.transformer.js').name,
    require('./securityGroup/securityGroup.reader.js').name,
    require('./cache/cacheConfigurer.service.js').name,
])
    .config(function(cloudProviderRegistryProvider) {
        cloudProviderRegistryProvider.registerProvider('cf', {
            name: 'Cloud Foundry',
            logo: {
                path: require('./logo/logo_cf.png'),
            },
            cache: {
                configurer: 'cfCacheConfigurer',
            },
            image: {
                reader: 'cfImageReader',
            },
            serverGroup: {
                transformer: 'cfServerGroupTransformer',
                detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
                detailsController: 'cfServerGroupDetailsCtrl',
                cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
                cloneServerGroupController: 'cfCloneServerGroupCtrl',
                commandBuilder: 'cfServerGroupCommandBuilder',
            //    configurationService: 'cfServerGroupConfigurationService',
            },
            instance: {
                instanceTypeService: 'cfInstanceTypeService',
                detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
                detailsController: 'cfInstanceDetailsCtrl',
            },
            loadBalancer: {
                transformer: 'cfLoadBalancerTransformer',
                detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
                detailsController: 'cfLoadBalancerDetailsCtrl',
                createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
                createLoadBalancerController: 'cfCreateLoadBalancerCtrl',
            },
            securityGroup: {
                transformer: 'cfSecurityGroupTransformer',
                reader: 'cfSecurityGroupReader',
                detailsTemplateUrl: require('./securityGroup/details/securityGroupDetails.html'),
                detailsController: 'cfSecurityGroupDetailsCtrl',
                //createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
                //createSecurityGroupController: 'cfCreateSecurityGroupCtrl',
            },
        });
    });

DeploymentStrategyRegistry.registerProvider('cf', ['redblack']);
