'use strict';

let angular = require('angular');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
    templates(key);
});

module.exports = angular.module('spinnaker.cf', [
    require('../core/cloudProvider/cloudProvider.registry.js'),
    require('../core/pipeline/config/stages/deploy/cf/cfDeployStage.js'),
    require('./instance/cfInstanceTypeService.js'),
    require('./serverGroup/details/serverGroupDetails.cf.controller.js'),
    require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
    require('./serverGroup/configure/wizard/CloneServerGroupCtrl.js'),
    require('./serverGroup/configure/serverGroup.configure.cf.module.js'),
    require('./serverGroup/serverGroup.transformer.js'),
    require('./loadBalancer/loadBalancer.transformer.js'),
    require('./loadBalancer/details/LoadBalancerDetailsCtrl.js'),
    require('./loadBalancer/configure/CreateLoadBalancerCtrl.js'),
    require('./instance/details/instance.details.controller.js'),
    require('./securityGroup/details/SecurityGroupDetailsCtrl.js'),
    require('./securityGroup/securityGroup.transformer.js'),
    require('./securityGroup/securityGroup.reader.js'),
    require('./cache/cacheConfigurer.service.js'),
])
    .config(function(cloudProviderRegistryProvider) {
        cloudProviderRegistryProvider.registerProvider('cf', {
            name: 'Cloud Foundry',
            logo: {
                path: require('./logo_cf.png'),
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
    }).name;
