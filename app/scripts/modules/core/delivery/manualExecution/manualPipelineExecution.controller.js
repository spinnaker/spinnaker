'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.manualPipelineExecution.controller', [
  require('../../utils/lodash.js'),
  require('../../pipeline/config/triggers/jenkins/jenkinsTrigger.module.js'),
])
  .controller('ManualPipelineExecutionCtrl', function($scope, $filter, _, igorService, $modalInstance, pipeline, application) {

    $scope.pipeline = pipeline;

    $scope.application = application;

    if (!pipeline) {
      $scope.pipelineOptions = application.pipelineConfigs;
    }

    $scope.command = {
      pipeline: pipeline,
      trigger: null,
      selectedBuild: null,
    };

    let addTriggers = () => {
      if (!$scope.command.pipeline) {
        $scope.command.trigger = null;
        return;
      }

      $scope.triggers = _.chain($scope.command.pipeline.triggers)
        .filter('type', 'jenkins')
        .sortBy('enabled')
        .map(function (trigger) {
          var copy = _.clone(trigger);
          copy.buildNumber = null;
          copy.type = 'manual';
          copy.description = copy.master + ': ' + copy.job;
          return copy;
        })
        .value();

      $scope.command.trigger  = _.first($scope.triggers);
      $scope.builds = [];
    };

    $scope.viewState = {
      triggering: false,
      buildsLoading: true,
    };

    $scope.triggerUpdated = function(trigger) {
      $scope.viewState.buildsLoading = true;
      let command = $scope.command;

      if( trigger !== undefined ) {
        command.trigger = trigger;
      }

      if (command.trigger) {
        $scope.viewState.buildsLoading = true;
        igorService.listBuildsForJob(command.trigger.master, command.trigger.job).then(function(builds) {
          $scope.builds = _.filter(builds, {building: false, result: 'SUCCESS'});
          if (!angular.isDefined(command.trigger.build)) {
            command.selectedBuild = $scope.builds[0];
          }
          $scope.viewState.buildsLoading = false;
        });
      } else {
        $scope.builds = [];
        $scope.viewState.buildsLoading = false;
      }
    };

    $scope.pipelineSelected = () => {
      let pipeline = $scope.command.pipeline;
      $scope.currentlyRunningExecutions = application.executions
        .filter((execution) => execution.pipelineConfigId === pipeline.id && execution.isActive);
      addTriggers();
      $scope.triggerUpdated();
      if (pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length){
        $scope.parameters = {};
        _.each(pipeline.parameterConfig, function(parameter) {
          $scope.parameters[parameter.name] = parameter.default;
        });
      }

    };


    $scope.updateSelectedBuild = function(item) {
      $scope.command.selectedBuild = item;
    };

    this.cancel = function() {
      $modalInstance.dismiss();
    };

    this.execute = function() {
      let selectedTrigger = $scope.command.trigger || {},
          command = { trigger: selectedTrigger },
          pipeline = $scope.command.pipeline;

      command.pipelineName = pipeline.name;

      if (selectedTrigger && $scope.command.selectedBuild) {
        selectedTrigger.buildNumber = $scope.command.selectedBuild.number;
      }
      if (pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length) {
        selectedTrigger.parameters = $scope.parameters;
      }
      $modalInstance.close(command);
    };

    if (pipeline) {
      $scope.pipelineSelected();
    }

  }).name;
