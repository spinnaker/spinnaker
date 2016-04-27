'use strict';

let angular = require('angular');


module.exports = angular.module('spinnaker.core.pipeline.stage.jenkinsStage', [
  require('../../../../ci/jenkins/igor.service.js'),
  require('../../pipelineConfigProvider.js'),
])
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
  }).controller('JenkinsStageCtrl', function($scope, stage, igorService) {

    $scope.stage = stage;
    $scope.stage.failPipeline = ($scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline);
    $scope.stage.continuePipeline = ($scope.stage.continuePipeline === undefined ? false : $scope.stage.continuePipeline);

    $scope.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      jobsLoaded: false,
      jobsRefreshing: false,
      failureOption: 'fail',
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
        let master = $scope.stage.master,
            job = $scope.stage.job || '';
        $scope.viewState.masterIsParameterized = master.indexOf('${') > -1;
        $scope.viewState.jobIsParameterized = job.indexOf('${') > -1;
        if ($scope.viewState.masterIsParameterized || $scope.viewState.jobIsParameterized) {
          $scope.viewState.jobsLoaded = true;
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
      let stage = $scope.stage,
          view = $scope.viewState;
      if (stage && stage.master && stage.job && !view.masterIsParameterized && !view.jobIsParameterized) {
        igorService.getJobConfig($scope.stage.master, $scope.stage.job).then((config) => {
          config = config || {};
          if(!$scope.stage.parameters) {
            $scope.stage.parameters = {};
          }
          $scope.jobParams = config.parameterDefinitionList;
          $scope.userSuppliedParameters = $scope.stage.parameters;
          $scope.useDefaultParameters = {};
          let params = $scope.jobParams || [];
          params.forEach((property) => {
            if(!(property.name in $scope.stage.parameters) && (property.defaultValue !== null)) {
              $scope.useDefaultParameters[property.name] = true;
            }
          });
       });
      }
    }

    this.failureOptionChanged = function() {
      if ($scope.viewState.failureOption === 'fail') {
        $scope.stage.failPipeline = true;
        $scope.stage.continuePipeline = false;
      } else if ($scope.viewState.failureOption === 'stop') {
        $scope.stage.failPipeline = false;
        $scope.stage.continuePipeline = false;
      } else if ($scope.viewState.failureOption === 'ignore') {
        $scope.stage.failPipeline = false;
        $scope.stage.continuePipeline = true;
      }
    };

    function initializeFailureOption() {
      var initValue = '';
      if ($scope.stage.failPipeline === true && $scope.stage.continuePipeline === false) {
        initValue = 'fail';
      } else if ($scope.stage.failPipeline === false && $scope.stage.continuePipeline === false) {
        initValue = 'stop';
      } else if ($scope.stage.failPipeline === false && $scope.stage.continuePipeline === true) {
        initValue = 'ignore';
      }
      $scope.viewState.failureOption = initValue;
    }

    initializeFailureOption();

    $scope.useDefaultParameters = {};
    $scope.userSuppliedParameters = {};

    this.updateParam = function(parameter) {
      if($scope.useDefaultParameters[parameter] === true) {
        delete $scope.userSuppliedParameters[parameter];
        delete $scope.stage.parameters[parameter];
      } else if($scope.userSuppliedParameters[parameter]) {
        $scope.stage.parameters[parameter] = $scope.userSuppliedParameters[parameter];
      }
    };

    initializeMasters();

    $scope.$watch('stage.master', updateJobsList);
    $scope.$watch('stage.job', updateJobConfig);

  });

