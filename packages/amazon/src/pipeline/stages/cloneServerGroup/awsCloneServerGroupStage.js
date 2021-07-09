'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, AppListExtractor, NameUtils, Registry, StageConstants } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_CLONESERVERGROUP_AWSCLONESERVERGROUPSTAGE =
  'spinnaker.amazon.pipeline.stage.cloneServerGroupStage';
export const name = AMAZON_PIPELINE_STAGES_CLONESERVERGROUP_AWSCLONESERVERGROUPSTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_CLONESERVERGROUP_AWSCLONESERVERGROUPSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'aws',
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
  .controller('awsCloneServerGroupStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.viewState = {
        accountsLoaded: false,
      };

      AccountService.listAccounts('aws').then((accounts) => {
        $scope.accounts = accounts;
        $scope.viewState.accountsLoaded = true;
      });

      this.cloneTargets = StageConstants.TARGET_LIST;
      stage.target = stage.target || this.cloneTargets[0].val;
      stage.application = $scope.application.name;
      stage.cloudProvider = 'aws';
      stage.cloudProviderType = 'aws';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Amazon'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.aws) {
        stage.credentials = $scope.application.defaultCredentials.aws;
      }

      if (stage.isNew) {
        stage.useAmiBlockDeviceMappings = _.get(
          $scope,
          'application.attributes.providerSettings.aws.useAmiBlockDeviceMappings',
          false,
        );
        stage.copySourceCustomBlockDeviceMappings = false; // default to using block device mappings from current instance type
      }

      this.targetClusterUpdated = () => {
        if (stage.targetCluster) {
          const filterByCluster = AppListExtractor.monikerClusterNameFilter(stage.targetCluster);
          const moniker = _.first(AppListExtractor.getMonikers([$scope.application], filterByCluster));
          if (moniker) {
            stage.stack = moniker.stack;
            stage.freeFormDetails = moniker.detail;
          } else {
            // if the user has entered a free-form value for the target cluster, fall back to the naming service
            const nameParts = NameUtils.parseClusterName(stage.targetCluster);
            stage.stack = nameParts.stack;
            stage.freeFormDetails = nameParts.freeFormDetails;
          }
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

      this.getBlockDeviceMappingsSource = () => {
        if (stage.copySourceCustomBlockDeviceMappings) {
          return 'source';
        } else if (stage.useAmiBlockDeviceMappings) {
          return 'ami';
        }
        return 'default';
      };

      this.selectBlockDeviceMappingsSource = (selection) => {
        if (selection === 'source') {
          // copy block device mappings from source asg
          stage.copySourceCustomBlockDeviceMappings = true;
          stage.useAmiBlockDeviceMappings = false;
        } else if (selection === 'ami') {
          // use block device mappings from selected ami
          stage.copySourceCustomBlockDeviceMappings = false;
          stage.useAmiBlockDeviceMappings = true;
        } else {
          // use default block device mappings for selected instance type
          stage.copySourceCustomBlockDeviceMappings = false;
          stage.useAmiBlockDeviceMappings = false;
        }
      };

      this.onRedBlackFieldChange = (key, value) => {
        _.set(stage, key, value);
      };
    },
  ]);
