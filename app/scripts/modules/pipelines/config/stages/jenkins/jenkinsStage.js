'use strict';

angular.module('deckApp.pipelines.stage.jenkins')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Jenkins',
      description: 'Runs a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsStageCtrl',
      controlelrAs: 'jenkinsStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/jenkins/jenkinsExecutionDetails.html',
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
      console.log('here');
      if ($scope.stage && $scope.stage.master) {
        console.log('here1');
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        jenkinsService.listJobsForMaster($scope.stage.master).then(function(jobs) {
          $scope.viewState.jobsLoaded = true;
          $scope.jobs = jobs;
          if ($scope.jobs.indexOf($scope.stage.job) === -1) {
            $scope.stage.job = '';
          }
        });
      }
    }

    $scope.$watch('stage.master', updateJobsList);

  });

