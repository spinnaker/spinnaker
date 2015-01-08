'use strict';

angular.module('deckApp.pipelines.trigger.jenkins')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Jenkins',
      description: 'Listens to a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsTriggerCtrl',
      controllerAs: 'jenkinsTriggerCtrl',
      templateUrl: 'scripts/modules/pipelines/config/triggers/jenkins/jenkinsTrigger.html'
    });
  })
  .controller('JenkinsTriggerCtrl', function($scope, trigger, igorService) {
    $scope.viewState = {
      mastersLoaded: false,
      jobsLoaded: false
    };

    $scope.trigger = trigger;

    igorService.listMasters().then(function(masters) {
      $scope.masters = masters;
      $scope.viewState.mastersLoaded = true;
    });

    function updateJobsList() {
      if ($scope.trigger && $scope.trigger.master) {
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        igorService.listJobsForMaster($scope.trigger.master).then(function(jobs) {
          $scope.viewState.jobsLoaded = true;
          $scope.jobs = jobs;
          if ($scope.jobs.indexOf($scope.trigger.job) === -1) {
            $scope.trigger.job = '';
          }
        });
      }
    }

    $scope.$watch('trigger.master', updateJobsList);

  });
