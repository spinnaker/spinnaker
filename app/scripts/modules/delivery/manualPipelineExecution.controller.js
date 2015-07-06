'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.manualPipelineExecution.controller', [
  require('utils/lodash.js'),
  require('../pipelines/config/triggers/jenkins/jenkinsTrigger.module.js'),
])
  .controller('ManualPipelineExecutionCtrl', function($scope, $filter, _, igorService, $modalInstance, pipeline, currentlyRunningExecutions) {

    $scope.pipeline = pipeline;
    $scope.currentlyRunningExecutions = currentlyRunningExecutions;

    if(pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length){
      $scope.parameters = {};
      _.each(pipeline.parameterConfig, function(parameter) {
        $scope.parameters[parameter.name] = parameter.default;
      });
    }

    $scope.triggers = _.chain(pipeline.triggers)
      .filter('type', 'jenkins')
      .sortBy('enabled')
      .map(function(trigger) {
        var copy = _.clone(trigger);
        copy.buildNumber = null;
        copy.type = 'manual';
        copy.description = copy.master + ': ' + copy.job;
        return copy;
      })
      .value();

    $scope.viewState = {
      triggering: false,
      buildsLoading: true,
    };

    $scope.trigger  = _.first($scope.triggers);
    $scope.builds = [];

    $scope.triggerUpdated = function(trigger) {

      if( trigger !== undefined ) {
        $scope.trigger = trigger;
      }

      if (angular.isDefined($scope.trigger)) {
        $scope.viewState.buildsLoading = true;
        igorService.listBuildsForJob($scope.trigger.master, $scope.trigger.job).then(function(builds) {
          $scope.builds = _.filter(builds, {building: false, result: 'SUCCESS'});
          if (!angular.isDefined($scope.trigger.build)) {
            $scope.selectedBuild = $scope.builds[0];
          }
          $scope.viewState.buildsLoading = false;
        });
      } else {
        $scope.builds = [];
        $scope.viewState.buildsLoading = false;
      }
    };

    $scope.updateSelectedBuild = function(item) {
      $scope.selectedBuild = item;
    };

    this.cancel = function() {
      $modalInstance.dismiss();
    };

    this.execute = function() {
      if ($scope.trigger && $scope.selectedBuild) {
        $scope.trigger.buildNumber = $scope.selectedBuild.number;
      }
      if (pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length) {
        if (!$scope.trigger) {
          $scope.trigger = {};
        }
        $scope.trigger.parameters = $scope.parameters;
      }
      $modalInstance.close($scope.trigger);
    };

    $scope.triggerUpdated();

  }).name;
