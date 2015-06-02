'use strict';

angular.module('spinnaker.pipelines.stage.pipeline')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Pipeline',
      description: 'Runs a pipeline',
      key: 'pipeline',
      controller: 'pipelineStageCtrl',
      controllerAs: 'pipelineStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/pipeline/pipelineStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/pipeline/pipelineExecutionDetails.html',
      executionLabelTemplateUrl: 'scripts/modules/pipelines/config/stages/pipeline/pipelineExecutionLabel.html',
      validators: [
        {
          type: 'requiredField',
          fieldName: 'pipeline',
          message: '<strong>Pipeline</strong> is a required field on pipeline stages.',
        },
      ],
    });
  }).controller('pipelineStageCtrl', function($scope, stage, pipelineConfigService, $filter, infrastructureCaches) {

    $scope.stage = stage;

    $scope.stage.application = $scope.application.name;

    $scope.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      mastersLastRefreshed: null,
      pipelinesLoaded : false,
      jobsRefreshing: false,
      jobsLastRefreshed: null,
    };

    function initializeMasters() {
      pipelineConfigService.getPipelinesForApplication($scope.stage.application).then(function (pipelines) {
        $scope.pipelines = pipelines;
        $scope.viewState.pipelinesLoaded = true;
      });
    }

    this.refreshMasters = function() {
      $scope.viewState.mastersRefreshing = true;
      $scope.viewState.mastersLastRefreshed = null;
      infrastructureCaches.clearCache('buildMasters');
      initializeMasters();
    };

    this.refreshJobs = function() {
      $scope.viewState.jobsRefreshing = true;
      $scope.viewState.jobsLastRefreshed = null;
      infrastructureCaches.clearCache('buildJobs');
      updateJobsList();
    };

    function updateJobsList() {
     /*
      if ($scope.stage && $scope.stage.master) {
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        igorService.listJobsForMaster($scope.stage.master).then(function(jobs) {
          $scope.viewState.jobsLastRefreshed = $filter('timestamp')(infrastructureCaches.buildJobs.getStats().ageMax);
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
      */
    }
/*
    function updateJobConfig() {
      if ($scope.stage && $scope.stage.master && $scope.stage.job) {
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
*/

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

    //$scope.$watch('stage.application', updatePipelineList);
    //$scope.$watch('stage.pipeline', updatePipelineConfig);

  });

