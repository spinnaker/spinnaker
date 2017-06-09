'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.pipeline.stage..tagImageStage', [
  ACCOUNT_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'gce',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
      executionConfigSections: ['tagImageConfig', 'taskStatus'],
    });
  })
  .controller('gceTagImageStageCtrl', function($scope, accountService) {
    accountService.listAccounts('gce').then(function(accounts) {
      $scope.accounts = accounts;
    });

    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'gce';
  });
