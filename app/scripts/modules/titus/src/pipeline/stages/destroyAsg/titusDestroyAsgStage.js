'use strict';

const angular = require('angular');

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.titus.pipeline.stage.destroyAsgStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      alias: 'destroyAsg',
      cloudProvider: 'titus',
      templateUrl: require('./destroyAsgStage.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('titusDestroyAsgStageCtrl', [
    '$scope',
    function($scope) {
      let stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('titus').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'titus';

      if (!stage.credentials && $scope.application.defaultCredentials.titus) {
        stage.credentials = $scope.application.defaultCredentials.titus;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.titus) {
        stage.regions.push($scope.application.defaultRegions.titus);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
