'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE = 'spinnaker.gce.pipeline.stage..enableAsgStage';
export const name = GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./enableAsgStage.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('gceEnableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'gce';

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
      if (!stage.regions.length && $scope.application.defaultRegions.gce) {
        stage.regions.push($scope.application.defaultRegions.gce);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
