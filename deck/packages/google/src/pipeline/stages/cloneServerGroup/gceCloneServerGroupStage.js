'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, NameUtils, Registry, StageConstants } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE =
  'spinnaker.gce.pipeline.stage..cloneServerGroupStage';
export const name = GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('gceCloneServerGroupStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.viewState = {
        accountsLoaded: false,
      };

      AccountService.listAccounts('gce').then((accounts) => {
        $scope.accounts = accounts;
        $scope.viewState.accountsLoaded = true;
      });

      this.cloneTargets = StageConstants.TARGET_LIST;
      stage.target = stage.target || this.cloneTargets[0].val;
      stage.application = $scope.application.name;
      stage.cloudProvider = 'gce';
      stage.cloudProviderType = 'gce';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Google'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.gce) {
        stage.credentials = $scope.application.defaultCredentials.gce;
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

      this.toggleDisableTraffic = () => {
        stage.disableTraffic = !stage.disableTraffic;
      };
    },
  ]);
