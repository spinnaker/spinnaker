'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.trigger.jenkins', [
  require('../trigger.directive.js'),
  require('restangular'),
  require('../../../../jenkins/index.js'),
  require('../../../../caches/cacheInitializer.js'),
  require('../../../../caches/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Jenkins',
      description: 'Listens to a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsTriggerCtrl',
      controllerAs: 'jenkinsTriggerCtrl',
      templateUrl: require('./jenkinsTrigger.html'),
      popoverLabelUrl: 'scripts/modules/pipelines/config/triggers/jenkins/jenkinsPopoverLabel.html'
    });
  })
  .controller('JenkinsTriggerCtrl', function($scope, trigger, igorService, cacheInitializer, infrastructureCaches, $filter) {

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
