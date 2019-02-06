'use strict';

const angular = require('angular');
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { IgorService, BuildServiceType } from 'core/ci/igor.service';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

import { JenkinsTriggerTemplate } from './JenkinsTriggerTemplate';
import { JenkinsTriggerExecutionStatus } from './JenkinsTriggerExecutionStatus';

module.exports = angular
  .module('spinnaker.core.pipeline.config.trigger.jenkins', [require('../trigger.directive').name])
  .config(function() {
    Registry.pipeline.registerTrigger({
      label: 'Jenkins',
      description: 'Listens to a Jenkins job',
      key: 'jenkins',
      controller: 'JenkinsTriggerCtrl',
      controllerAs: 'jenkinsTriggerCtrl',
      templateUrl: require('./jenkinsTrigger.html'),
      manualExecutionComponent: JenkinsTriggerTemplate,
      executionStatusComponent: JenkinsTriggerExecutionStatus,
      executionTriggerLabel: () => 'Triggered Build',
      providesVersionForBake: true,
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
        },
      ],
    });
  })
  .controller('JenkinsTriggerCtrl', function($scope, trigger) {
    $scope.trigger = trigger;
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

    $scope.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      jobsLoaded: false,
      jobsRefreshing: false,
    };

    function initializeMasters() {
      IgorService.listMasters(BuildServiceType.Jenkins).then(function(masters) {
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
        IgorService.listJobsForMaster($scope.trigger.master).then(function(jobs) {
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
