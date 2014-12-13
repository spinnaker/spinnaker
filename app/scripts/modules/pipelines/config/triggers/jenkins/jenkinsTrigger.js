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
    $scope.trigger = trigger;
    igorService.listMasters().then(function(masters) {
      $scope.masters = masters;
    });

    function updateJobsList() {
      if ($scope.trigger && $scope.trigger.master) {
        igorService.listJobsForMaster($scope.trigger.master).then(function(jobs) {
          $scope.jobs = jobs;
        });
      }
    }

    $scope.$watch('trigger.master', updateJobsList);

  });
