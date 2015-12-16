'use strict';

let angular = require('angular');


module.exports = angular.module('spinnaker.core.pipeline.stage.jenkinsStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Jenkins',
      description: 'Runs a Jenkins job',
      key: 'jenkins',
      restartable: true,
      controller: 'JenkinsStageCtrl',
      controllerAs: 'jenkinsStageCtrl',
      templateUrl: require('./jenkinsStage.html'),
      executionDetailsUrl: require('./jenkinsExecutionDetails.html'),
      executionLabelTemplateUrl: require('./jenkinsExecutionLabel.html'),
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [
        { type: 'requiredField', fieldName: 'job', },
      ],
      strategy: true,
    });
  }).controller('JenkinsStageCtrl', function($scope, stage, igorService, _) {

    $scope.stage = stage;

    $scope.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      jobsLoaded: false,
      jobsRefreshing: false,
    };

    function initializeMasters() {
      igorService.listMasters().then(function (masters) {
        $scope.masters = masters;
        $scope.viewState.mastersLoaded = true;
        $scope.viewState.mastersRefreshing = false;
      });
    }

    this.refreshMasters = function() {
      $scope.viewState.mastersRefreshing = true;
      initializeMasters();
    };

    this.refreshJobs = function() {
      $scope.viewState.jobsRefreshing = true;
      updateJobsList();
    };

    function updateJobsList() {
      if ($scope.stage && $scope.stage.master) {
        $scope.viewState.masterIsParameterized = $scope.stage.master.indexOf('${') > -1;
        if ($scope.viewState.masterIsParameterized) {
          return;
        }
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        igorService.listJobsForMaster($scope.stage.master).then(function(jobs) {
          $scope.viewState.jobsLoaded = true;
          $scope.viewState.jobsRefreshing = false;
          $scope.jobs = jobs;
          if ($scope.jobs.indexOf($scope.stage.job) === -1) {
            $scope.stage.job = '';
          }
        });
        $scope.useDefaultParameters = {};
        $scope.userSuppliedParameters = {};
        $scope.jobParams = null;
      }
    }

    function updateJobConfig() {
      if ($scope.stage && $scope.stage.master && $scope.stage.job && !$scope.viewState.masterIsParameterized) {
        igorService.getJobConfig($scope.stage.master, $scope.stage.job).then(function(config){
        if(!$scope.stage.parameters) {
          $scope.stage.parameters = {};
        }
        $scope.jobParams = config.parameterDefinitionList;
        $scope.userSuppliedParameters = $scope.stage.parameters;
        $scope.useDefaultParameters = {};
         _.each($scope.jobParams, function(property){
            if(!(property.name in $scope.stage.parameters) && (property.defaultValue!==null)){
              $scope.useDefaultParameters[property.name] = true;
            }
         });
       });
      }
    }

    $scope.useDefaultParameters = {};
    $scope.userSuppliedParameters = {};

    this.updateParam = function(parameter){
      if($scope.useDefaultParameters[parameter] === true){
        delete $scope.userSuppliedParameters[parameter];
        delete $scope.stage.parameters[parameter];
      } else if($scope.userSuppliedParameters[parameter]){
        $scope.stage.parameters[parameter] = $scope.userSuppliedParameters[parameter];
      }
    };

    initializeMasters();

    $scope.$watch('stage.master', updateJobsList);
    $scope.$watch('stage.job', updateJobConfig);

  });

