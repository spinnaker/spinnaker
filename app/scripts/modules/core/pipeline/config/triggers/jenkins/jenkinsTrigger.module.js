'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.jenkins', [
  require('./jenkinsTriggerOptions.directive.js'),
  require('../trigger.directive.js'),
  require('core/ci/jenkins/igor.service.js'),
  require('core/serviceAccount/serviceAccount.service.js'),
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
      manualExecutionHandler: 'jenkinsTriggerExecutionHandler',
      validators: [
        {
          type: 'requiredField',
          fieldName: 'job',
          message: '<strong>Job</strong> is a required field on Jenkins triggers.',
        },
      ],
    });
  })
  .factory('jenkinsTriggerExecutionHandler', function ($q) {
    // must provide two fields:
    //   formatLabel (promise): used to supply the label for selecting a trigger when there are multiple triggers
    //   selectorTemplate: provides the HTML to show extra fields
    return {
      formatLabel: (trigger) => {
        return $q.when(`(Jenkins) ${trigger.master}: ${trigger.job}`);
      },
      selectorTemplate: require('./selectorTemplate.html'),
    };
  })
  .controller('JenkinsTriggerCtrl', function($scope, trigger, igorService, settings, serviceAccountService) {

    $scope.trigger = trigger;
    this.fiatEnabled = settings.feature.fiatEnabled;
    serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

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
          if (jobs.length && !$scope.jobs.includes($scope.trigger.job)) {
            $scope.trigger.job = '';
          }
        });
      }
    }

    initializeMasters();

    $scope.$watch('trigger.master', updateJobsList);

  });
