'use strict';

import { module } from 'angular';
import { pickBy } from 'lodash';

import { JenkinsExecutionLabel } from './JenkinsExecutionLabel';
import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE = 'spinnaker.core.pipeline.stage.jenkinsStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      label: 'Jenkins',
      description: 'Runs a Jenkins job',
      key: 'jenkins',
      restartable: true,
      controller: 'JenkinsStageCtrl',
      controllerAs: 'jenkinsStageCtrl',
      producesArtifacts: true,
      templateUrl: require('./jenkinsStage.html'),
      executionDetailsUrl: require('./jenkinsExecutionDetails.html'),
      executionLabelComponent: JenkinsExecutionLabel,
      providesVersionForBake: true,
      extraLabelLines: (stage) => {
        if (!stage.masterStage.context || !stage.masterStage.context.buildInfo) {
          return 0;
        }
        const lines = stage.masterStage.context.buildInfo.number ? 1 : 0;
        return lines + (stage.masterStage.context.buildInfo.testResults || []).length;
      },
      supportsCustomTimeout: true,
      validators: [{ type: 'requiredField', fieldName: 'job' }],
      strategy: true,
    });
  })
  .controller('JenkinsStageCtrl', [
    '$scope',
    'stage',
    function ($scope, stage) {
      $scope.stage = stage;
      $scope.stage.failPipeline = $scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline;
      $scope.stage.continuePipeline =
        $scope.stage.continuePipeline === undefined ? false : $scope.stage.continuePipeline;

      $scope.viewState = {
        mastersLoaded: false,
        mastersRefreshing: false,
        jobsLoaded: false,
        jobsRefreshing: false,
        failureOption: 'fail',
        markUnstableAsSuccessful: !!stage.markUnstableAsSuccessful,
        waitForCompletion: stage.waitForCompletion || stage.waitForCompletion === undefined,
      };

      // Using viewState to avoid marking pipeline as dirty if field is not set
      this.markUnstableChanged = () => (stage.markUnstableAsSuccessful = $scope.viewState.markUnstableAsSuccessful);

      this.waitForCompletionChanged = () => (stage.waitForCompletion = $scope.viewState.waitForCompletion);

      function initializeMasters() {
        IgorService.listMasters(BuildServiceType.Jenkins).then(function (masters) {
          $scope.masters = masters;
          $scope.viewState.mastersLoaded = true;
          $scope.viewState.mastersRefreshing = false;
        });
      }

      this.refreshMasters = function () {
        $scope.viewState.mastersRefreshing = true;
        initializeMasters();
      };

      this.refreshJobs = function () {
        $scope.viewState.jobsRefreshing = true;
        updateJobsList();
      };

      function updateJobsList() {
        if ($scope.stage && $scope.stage.master) {
          const master = $scope.stage.master;
          const job = $scope.stage.job || '';
          $scope.viewState.masterIsParameterized = master.includes('${');
          $scope.viewState.jobIsParameterized = job.includes('${');
          if ($scope.viewState.masterIsParameterized || $scope.viewState.jobIsParameterized) {
            $scope.viewState.jobsLoaded = true;
            return;
          }
          $scope.viewState.jobsLoaded = false;
          $scope.jobs = [];
          IgorService.listJobsForMaster($scope.stage.master).then(function (jobs) {
            $scope.viewState.jobsLoaded = true;
            $scope.viewState.jobsRefreshing = false;
            $scope.jobs = jobs;
            if (!$scope.jobs.includes($scope.stage.job)) {
              $scope.stage.job = '';
            }
          });
          $scope.useDefaultParameters = {};
          $scope.userSuppliedParameters = {};
          $scope.jobParams = null;
        }
      }

      function updateJobConfig() {
        const stage = $scope.stage;
        const view = $scope.viewState;

        if (stage && stage.master && stage.job && !view.masterIsParameterized && !view.jobIsParameterized) {
          IgorService.getJobConfig($scope.stage.master, $scope.stage.job).then((config) => {
            config = config || {};
            if (!$scope.stage.parameters) {
              $scope.stage.parameters = {};
            }
            $scope.jobParams = config.parameterDefinitionList;
            $scope.userSuppliedParameters = $scope.stage.parameters;
            $scope.useDefaultParameters = {};

            if ($scope.jobParams) {
              const acceptedJobParameters = $scope.jobParams.map((param) => param.name);
              $scope.invalidParameters = pickBy(
                $scope.userSuppliedParameters,
                (value, name) => !acceptedJobParameters.includes(name),
              );
            }

            const params = $scope.jobParams || [];
            params.forEach((property) => {
              if (!(property.name in $scope.stage.parameters) && property.defaultValue !== null) {
                $scope.useDefaultParameters[property.name] = true;
              }
            });
          });
        }
      }

      $scope.hasInvalidParameters = () => Object.keys($scope.invalidParameters || {}).length;
      $scope.useDefaultParameters = {};
      $scope.userSuppliedParameters = {};

      this.updateParam = function (parameter) {
        if ($scope.useDefaultParameters[parameter] === true) {
          delete $scope.userSuppliedParameters[parameter];
          delete $scope.stage.parameters[parameter];
        } else if ($scope.userSuppliedParameters[parameter]) {
          $scope.stage.parameters[parameter] = $scope.userSuppliedParameters[parameter];
        }
      };

      this.removeInvalidParameters = function () {
        Object.keys($scope.invalidParameters).forEach((param) => {
          if ($scope.stage.parameters[param] !== 'undefined') {
            delete $scope.stage.parameters[param];
          }
        });
        $scope.invalidParameters = {};
      };

      initializeMasters();

      $scope.$watch('stage.master', updateJobsList);
      $scope.$watch('stage.job', updateJobConfig);
    },
  ]);
