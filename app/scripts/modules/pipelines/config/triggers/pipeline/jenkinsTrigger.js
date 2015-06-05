'use strict';

angular.module('spinnaker.pipelines.trigger.pipeline')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Pipeline',
      description: 'Listens to a pipeline job',
      key: 'pipeline',
      controller: 'pipelineTriggerCtrl',
      controllerAs: 'pipelineTriggerCtrl',
      templateUrl: 'scripts/modules/pipelines/config/triggers/pipeline/pipelineTrigger.html',
      popoverLabelUrl: 'scripts/modules/pipelines/config/triggers/pipeline/pipelinePopoverLabel.html'
    });
  })
  .controller('pipelineTriggerCtrl', function($scope, trigger, igorService, cacheInitializer, infrastructureCaches, $filter) {

    $scope.trigger = trigger;

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
      if ($scope.trigger && $scope.trigger.master) {
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        igorService.listJobsForMaster($scope.trigger.master).then(function(jobs) {
          $scope.viewState.jobsLastRefreshed = $filter('timestamp')(infrastructureCaches.buildJobs.getStats().ageMax);
          $scope.viewState.jobsLoaded = true;
          $scope.viewState.jobsRefreshing = false;
          $scope.jobs = jobs;
          if ($scope.jobs.indexOf($scope.trigger.job) === -1) {
            $scope.trigger.job = '';
          }
        });
      }
    }

    initializeMasters();

    $scope.$watch('trigger.master', updateJobsList);

  });
