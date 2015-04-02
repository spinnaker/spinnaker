'use strict';

angular.module('deckApp.pipelines.stage.jenkins')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Jenkins',
      description: 'Runs a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsStageCtrl',
      controllerAs: 'jenkinsStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsExecutionDetails.html',
      executionLabelTemplateUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsExecutionLabel.html',
      validators: [
        {
          type: 'requiredField',
          fieldName: 'job',
          message: '<strong>Job</strong> is a required field on Jenkins stages.',
        },
      ],
    });
  }).controller('JenkinsStageCtrl', function($scope, stage, igorService, $filter, infrastructureCaches) {

    $scope.stage = stage;

    $scope.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      mastersLastRefreshed: null,
      jobsLoaded: false,
      jobsRefreshing: false,
      jobsLastRefreshed: null,
    };

    function initializeMasters() {
      igorService.listMasters().then(function (masters) {
        $scope.masters = masters;
        $scope.viewState.mastersLoaded = true;
        $scope.viewState.mastersRefreshing = false;
        $scope.viewState.mastersLastRefreshed = $filter('timestamp')(infrastructureCaches.buildMasters.getStats().ageMax);
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
    }

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

