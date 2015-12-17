'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.jenkins', [
  require('../trigger.directive.js'),
  require('../../../../ci/jenkins/igor.service.js'),
  require('../../pipelineConfigProvider.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Jenkins',
      description: 'Listens to a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsTriggerCtrl',
      controllerAs: 'jenkinsTriggerCtrl',
      templateUrl: require('./jenkinsTrigger.html'),
      popoverLabelUrl: require('./jenkinsPopoverLabel.html'),
      validators: [
        {
          type: 'requiredField',
          fieldName: 'job',
          message: '<strong>Job</strong> is a required field on Jenkins triggers.',
        },
      ],
    });
  })
  .controller('JenkinsTriggerCtrl', function($scope, trigger, igorService) {

    $scope.trigger = trigger;

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
      if ($scope.trigger && $scope.trigger.master) {
        $scope.viewState.jobsLoaded = false;
        $scope.jobs = [];
        igorService.listJobsForMaster($scope.trigger.master).then(function(jobs) {
          $scope.viewState.jobsLoaded = true;
          $scope.viewState.jobsRefreshing = false;
          $scope.jobs = jobs;
          if (jobs.length && $scope.jobs.indexOf($scope.trigger.job) === -1) {
            $scope.trigger.job = '';
          }
        });
      }
    }

    initializeMasters();

    $scope.$watch('trigger.master', updateJobsList);

  });
