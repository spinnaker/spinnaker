'use strict';

const angular = require('angular');

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.pipeline.stage.destroyAsgStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'oracle',
      templateUrl: require('./destroyAsgStage.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
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
  .controller('oracleDestroyAsgStageCtrl', ['$scope', function($scope) {
    let stage = $scope.stage;
    let provider = 'oracle';

    $scope.targets = StageConstants.TARGET_LIST;
    stage.regions = stage.regions || [];
    stage.cloudProvider = provider;
    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    init();

    function init() {
      AccountService.listAccounts(provider).then(accounts => {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      if (!stage.credentials && $scope.application.defaultCredentials.oracle) {
        stage.credentials = $scope.application.defaultCredentials.oracle;
      }

      if (!stage.regions.length && $scope.application.defaultRegions.oracle) {
        stage.regions.push($scope.application.defaultRegions.oracle);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    }
  }]);
