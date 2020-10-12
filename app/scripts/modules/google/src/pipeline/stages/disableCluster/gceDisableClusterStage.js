'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE =
  'spinnaker.gce.pipeline.stage..disableClusterStage';
export const name = GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'gce',
      templateUrl: require('./disableClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        {
          type: 'requiredField',
          fieldName: 'remainingEnabledServerGroups',
          fieldLabel: 'Keep [X] enabled Server Groups',
        },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('gceDisableClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

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

      if (stage.remainingEnabledServerGroups === undefined) {
        stage.remainingEnabledServerGroups = 1;
      }

      ctrl.pluralize = function (str, val) {
        if (val === 1) {
          return str;
        }
        return str + 's';
      };

      if (stage.preferLargerOverNewer === undefined) {
        stage.preferLargerOverNewer = 'false';
      }
      stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
    },
  ]);
