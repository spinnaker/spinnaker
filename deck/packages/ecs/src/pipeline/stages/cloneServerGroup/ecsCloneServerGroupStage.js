'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, NameUtils, Registry, StageConstants } from '@spinnaker/core';

export const ECS_PIPELINE_STAGES_CLONESERVERGROUP_ECSCLONESERVERGROUPSTAGE =
  'spinnaker.ecs.pipeline.stage.cloneServerGroupStage';
export const name = ECS_PIPELINE_STAGES_CLONESERVERGROUP_ECSCLONESERVERGROUPSTAGE; // for backwards compatibility
module(ECS_PIPELINE_STAGES_CLONESERVERGROUP_ECSCLONESERVERGROUPSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'ecs',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      accountExtractor: (stage) => [stage.context.credentials],
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('ecsCloneServerGroupStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.viewState = {
        accountsLoaded: false,
      };

      AccountService.listAccounts('ecs').then((accounts) => {
        $scope.accounts = accounts;
        $scope.viewState.accountsLoaded = true;
      });

      this.cloneTargets = StageConstants.TARGET_LIST;
      stage.target = stage.target || this.cloneTargets[0].val;
      stage.application = $scope.application.name;
      stage.cloudProvider = 'ecs';
      stage.cloudProviderType = 'ecs';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Ecs'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.ecs) {
        stage.credentials = $scope.application.defaultCredentials.ecs;
      }

      this.targetClusterUpdated = () => {
        if (stage.targetCluster) {
          const clusterName = NameUtils.parseServerGroupName(stage.targetCluster);
          stage.stack = clusterName.stack;
          stage.freeFormDetails = clusterName.freeFormDetails;
        } else {
          stage.stack = '';
          stage.freeFormDetails = '';
        }
      };

      $scope.$watch('stage.targetCluster', this.targetClusterUpdated);

      this.removeCapacity = () => {
        delete stage.capacity;
      };

      if (!_.has(stage, 'useSourceCapacity')) {
        stage.useSourceCapacity = true;
      }

      this.toggleSuspendedProcess = (process) => {
        stage.suspendedProcesses = stage.suspendedProcesses || [];
        const processIndex = stage.suspendedProcesses.indexOf(process);
        if (processIndex === -1) {
          stage.suspendedProcesses.push(process);
        } else {
          stage.suspendedProcesses.splice(processIndex, 1);
        }
      };

      this.processIsSuspended = (process) => {
        return stage.suspendedProcesses && stage.suspendedProcesses.includes(process);
      };
    },
  ]);
