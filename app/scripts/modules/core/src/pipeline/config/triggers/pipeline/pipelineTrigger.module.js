'use strict';

import _ from 'lodash';
const angular = require('angular');

import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { PipelineTriggerTemplate } from './PipelineTriggerTemplate';
import { ExecutionUserStatus } from 'core/pipeline/status/ExecutionUserStatus';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

module.exports = angular
  .module('spinnaker.core.pipeline.config.trigger.pipeline', [require('../trigger.directive').name])
  .config(function() {
    Registry.pipeline.registerTrigger({
      label: 'Pipeline',
      description: 'Listens to a pipeline execution',
      key: 'pipeline',
      controller: 'pipelineTriggerCtrl',
      controllerAs: 'pipelineTriggerCtrl',
      templateUrl: require('./pipelineTrigger.html'),
      manualExecutionComponent: PipelineTriggerTemplate,
      executionStatusComponent: ExecutionUserStatus,
      executionTriggerLabel: () => 'Pipeline',
    });
  })
  .controller('pipelineTriggerCtrl', [
    '$scope',
    'trigger',
    function($scope, trigger) {
      $scope.trigger = trigger;

      this.fiatEnabled = SETTINGS.feature.fiatEnabled;
      ServiceAccountReader.getServiceAccounts().then(accounts => {
        this.serviceAccounts = accounts || [];
      });

      if (!$scope.trigger.application) {
        $scope.trigger.application = $scope.application.name;
      }

      if (!$scope.trigger.status) {
        $scope.trigger.status = [];
      }

      $scope.statusOptions = ['successful', 'failed', 'canceled'];

      function init() {
        if ($scope.trigger.application) {
          PipelineConfigService.getPipelinesForApplication($scope.trigger.application).then(function(pipelines) {
            $scope.pipelines = _.filter(pipelines, function(pipeline) {
              return pipeline.id !== $scope.pipeline.id;
            });
            if (
              !_.find(pipelines, function(pipeline) {
                return pipeline.id === $scope.trigger.pipeline;
              })
            ) {
              $scope.trigger.pipeline = null;
            }
            $scope.viewState.pipelinesLoaded = true;
          });
        }
      }

      $scope.viewState = {
        pipelinesLoaded: false,
        infiniteScroll: {
          numToAdd: 20,
          currentItems: 20,
        },
      };

      this.addMoreItems = function() {
        $scope.viewState.infiniteScroll.currentItems += $scope.viewState.infiniteScroll.numToAdd;
      };

      ApplicationReader.listApplications().then(function(applications) {
        $scope.applications = _.map(applications, 'name').sort();
      });

      $scope.useDefaultParameters = {};
      $scope.userSuppliedParameters = {};

      this.updateParam = function(parameter) {
        if ($scope.useDefaultParameters[parameter] === true) {
          delete $scope.userSuppliedParameters[parameter];
          delete $scope.trigger.parameters[parameter];
        } else if ($scope.userSuppliedParameters[parameter]) {
          $scope.trigger.pipelineParameters[parameter] = $scope.userSuppliedParameters[parameter];
        }
      };

      init();

      $scope.$watch('trigger.application', init);
    },
  ]);
