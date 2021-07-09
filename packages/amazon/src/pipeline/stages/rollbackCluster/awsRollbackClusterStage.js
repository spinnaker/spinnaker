'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_ROLLBACKCLUSTER_AWSROLLBACKCLUSTERSTAGE =
  'spinnaker.amazon.pipeline.stage.rollbackClusterStage';
export const name = AMAZON_PIPELINE_STAGES_ROLLBACKCLUSTER_AWSROLLBACKCLUSTERSTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_ROLLBACKCLUSTER_AWSROLLBACKCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'rollbackCluster',
      cloudProvider: 'aws',
      templateUrl: require('./rollbackClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('awsRollbackClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      ctrl.reset = () => {
        ctrl.accountUpdated();
        ctrl.resetSelectedCluster();
      };

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'aws';
      stage.targetHealthyRollbackPercentage = stage.targetHealthyRollbackPercentage || 100;

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
      if (!stage.regions.length && $scope.application.defaultRegions.aws) {
        stage.regions.push($scope.application.defaultRegions.aws);
      }
    },
  ]);
