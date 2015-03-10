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
      executionLabelTemplateUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsExecutionLabel.html'
    });
  }).controller('JenkinsStageCtrl', function($scope, stage, jenkinsService) {
    $scope.stage = stage;

    $scope.viewState = {
      mastersLoaded: false,
      jobsLoaded: false
    };

    jenkinsService.listMasters().then(function(masters) {
      $scope.masters = masters;
      $scope.viewState.mastersLoaded = true;
    });

    function updateJobsList() {
      if ($scope.stage && $scope.stage.master) {
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        jenkinsService.listJobsForMaster($scope.stage.master).then(function(jobs) {
          $scope.viewState.jobsLoaded = true;
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
         jenkinsService.getJobConfig($scope.stage.master, $scope.stage.job).then(function(config){
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

    $scope.$watch('stage.master', updateJobsList);
    $scope.$watch('stage.job', updateJobConfig);

    $scope.useDefaultParameters = {};
    $scope.userSuppliedParameters = {};

    $scope.updateParam = function(parameter){
      if($scope.useDefaultParameters[parameter] === true){
        delete $scope.userSuppliedParameters[parameter];
        delete $scope.stage.parameters[parameter];
      } else if($scope.userSuppliedParameters[parameter]){
        $scope.stage.parameters[parameter] = $scope.userSuppliedParameters[parameter];
      }
    };

  });

