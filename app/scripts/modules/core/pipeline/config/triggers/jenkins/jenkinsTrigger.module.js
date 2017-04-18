'use strict';

let angular = require('angular');
import {SERVICE_ACCOUNT_SERVICE} from 'core/serviceAccount/serviceAccount.service.ts';
import {IGOR_SERVICE, BuildServiceType} from 'core/ci/igor.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {SETTINGS} from 'core/config/settings';

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.jenkins', [
  require('./jenkinsTriggerOptions.directive.js'),
  require('../trigger.directive.js'),
  IGOR_SERVICE,
  SERVICE_ACCOUNT_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
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
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's Jenkins trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        }
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
  .controller('JenkinsTriggerCtrl', function($scope, trigger, igorService, serviceAccountService) {

    $scope.trigger = trigger;
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
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
      igorService.listMasters(BuildServiceType.Jenkins).then(function (masters) {
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
