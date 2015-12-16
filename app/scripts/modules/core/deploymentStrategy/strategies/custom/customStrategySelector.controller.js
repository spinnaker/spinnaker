'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom.customStrategySelectorController', [
    ])
    .controller('CustomStrategySelectorController', function($scope, pipelineConfigService, applicationReader, _) {

        if(!$scope.command.strategyApplication){
            $scope.command.strategyApplication = $scope.command.application;
        }

        $scope.viewState = {
            mastersLoaded: false,
            mastersRefreshing: false,
            mastersLastRefreshed: null,
            pipelinesLoaded : false,
            jobsRefreshing: false,
            jobsLastRefreshed: null,
            infiniteScroll: {
                numToAdd: 20,
                currentItems: 20,
            },
        };

        this.addMoreItems = function() {
            $scope.viewState.infiniteScroll.currentItems += $scope.viewState.infiniteScroll.numToAdd;
        };

        applicationReader.listApplications().then(function(applications) {
            $scope.applications = _.pluck(applications, 'name').sort();
            initializeMasters();
        });

        function initializeMasters() {
            if ($scope.command.application) {
                pipelineConfigService.getStrategiesForApplication($scope.command.application).then(function (pipelines) {
                    $scope.pipelines = pipelines;
                    if (!_.find( pipelines, function(pipeline) { return pipeline.id === $scope.command.strategyPipeline; })) {
                        $scope.command.strategyPipeline = null;
                    }
                    $scope.viewState.pipelinesLoaded = true;
                    updatePipelineConfig();
                });
            }
        }

        function updatePipelineConfig() {
            if ($scope.command && $scope.command.strategyApplication && $scope.command.strategyPipeline) {
                var config = _.find( $scope.pipelines, function(pipeline){ return pipeline.id === $scope.command.strategyPipeline; } );
                if(config && config.parameterConfig) {
                    if (!$scope.command.pipelineParameters) {
                        $scope.command.pipelineParameters = {};
                    }
                    $scope.pipelineParameters = config.parameterConfig;
                    $scope.userSuppliedParameters = $scope.command.pipelineParameters;
                    $scope.useDefaultParameters = {};
                    _.each($scope.pipelineParameters, function (property) {
                        if (!(property.name in $scope.command.pipelineParameters) && (property.default !== null)) {
                            $scope.useDefaultParameters[property.name] = true;
                        }
                    });
                } else {
                    clearParams();
                }
            } else {
                clearParams();
            }
        }

        function clearParams() {
            $scope.pipelineParameters = {};
            $scope.useDefaultParameters = {};
            $scope.userSuppliedParameters = {};
        }

        $scope.useDefaultParameters = {};
        $scope.userSuppliedParameters = {};

        this.updateParam = function(parameter){
            if($scope.useDefaultParameters[parameter] === true){
                delete $scope.userSuppliedParameters[parameter];
                delete $scope.command.parameters[parameter];
            } else if($scope.userSuppliedParameters[parameter]){
                $scope.command.pipelineParameters[parameter] = $scope.userSuppliedParameters[parameter];
            }
        };

        $scope.$watch('command.strategyApplication', initializeMasters);
        $scope.$watch('command.strategyPipeline', updatePipelineConfig);

    });
