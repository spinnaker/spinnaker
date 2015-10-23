'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.deploy', [
    require('../../../../../utils/lodash.js'),
    require('../../stageConstants.js'),
    require('../deployExecutionDetails.controller.js')
])
    .config(function(pipelineConfigProvider) {

        pipelineConfigProvider.registerStage({
            label: 'Deploy',
            description: 'Deploys to Cloud Foundry',
            cloudProvider: 'cf',
            key: 'linearDeploy_cf',
            templateUrl: require('./deployStage.html'),
            executionDetailsUrl: require('../deployExecutionDetails.html'),
            controller: 'DeployCloudFoundryStageCtrl',
            controllerAs: 'deployCloudFoundryStageCtrl',
            defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
        });

    })
    .controller('DeployCloudFoundryStageCtrl', function($scope, $uibModal, stage, namingService, providerSelectionService,
                                        cloudProviderRegistry, serverGroupCommandBuilder, serverGroupTransformer) {

        $scope.stage = stage;

        function initializeCommand() {
            // TODO: We can probably remove this once we've migrated everyone over to multi-cluster deploy stages.
            // This is the lazy way to get us there without explicitly editing all the existing pipelines
            $scope.stage.clusters = $scope.stage.clusters || [];
            if ($scope.stage.cluster) {
                $scope.stage.cluster.account = $scope.stage.account;
                $scope.stage.clusters = [$scope.stage.cluster];

                delete $scope.stage.cluster;
                delete $scope.stage.account;
            }
        }

        this.getRegion = function(cluster) {
            var availabilityZones = cluster.availabilityZones;
            if (availabilityZones) {
                var regions = Object.keys(availabilityZones);
                if (regions && regions.length) {
                    return regions[0];
                }
            }
            return 'n/a';
        };

        this.getClusterName = function(cluster) {
            return namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
        };

        this.addCluster = function() {
            providerSelectionService.selectProvider().then(function(selectedProvider) {
                let config = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
                $uibModal.open({
                    templateUrl: config.cloneServerGroupTemplateUrl,
                    controller: `${config.cloneServerGroupController} as ctrl`,
                    resolve: {
                        title: function () {
                            return 'Configure CF Deployment Cluster';
                        },
                        application: function () {
                            return $scope.application;
                        },
                        serverGroupCommand: function () {
                            return serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(selectedProvider);
                        },
                    }
                }).result.then(function(command) {
                    // If we don't set the provider, the serverGroupTransformer won't know which provider to delegate to.
                    command.provider = selectedProvider;
                    var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
                    delete stageCluster.credentials;
                    $scope.stage.clusters.push(stageCluster);
                });
            });
        };

        this.editCluster = function(cluster, index) {
            cluster.provider = cluster.providerType || 'cf';
            let providerConfig = cloudProviderRegistry.getProvider(cluster.provider);
            return $uibModal.open({
                templateUrl: providerConfig.serverGroup.cloneServerGroupTemplateUrl,
                controller: `${providerConfig.serverGroup.cloneServerGroupController} as ctrl`,
                resolve: {
                    title: function () {
                        return 'Configure Deployment Cluster';
                    },
                    application: function () {
                        return $scope.application;
                    },
                    serverGroupCommand: function () {
                        return serverGroupCommandBuilder.buildServerGroupCommandFromPipeline($scope.application, cluster);
                    },
                }
            }).result.then(function(command) {
                    var stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
                    delete stageCluster.credentials;
                    $scope.stage.clusters[index] = stageCluster;
                });
        };


        initializeCommand();

    }).name;
