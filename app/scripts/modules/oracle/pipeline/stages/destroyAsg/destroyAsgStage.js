'use strict';

const angular = require('angular');

import {
  StageConstants,
  PipelineTemplates
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.oraclebmcs.pipeline.stage.destroyAsgStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'oraclebmcs',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: PipelineTemplates.destroyAsgExecutionDetails,
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('oraclebmcsDestroyAsgStageCtrl', function($scope, accountService) {

    let stage = $scope.stage;
    let provider = 'oraclebmcs';

    $scope.targets = StageConstants.TARGET_LIST;
    stage.regions = stage.regions || [];
    stage.cloudProvider = provider;
    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    init();

    function init () {
      accountService.listAccounts(provider).then(accounts => {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      if (!stage.credentials && $scope.application.defaultCredentials.oraclebmcs) {
        stage.credentials = $scope.application.defaultCredentials.oraclebmcs;
      }

      if (!stage.regions.length && $scope.application.defaultRegions.oraclebmcs) {
        stage.regions.push($scope.application.defaultRegions.oraclebmcs);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    }
  });
