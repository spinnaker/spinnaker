'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.tagImageStage', [
  ACCOUNT_SERVICE,
  require('core/pipeline/config/pipelineConfigProvider.js'),
  require('./tagImageExecutionDetails.controller.js'),
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'gce',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
    });
  })
  .controller('gceTagImageStageCtrl', function($scope, accountService) {
    accountService.listAccounts('gce').then(function(accounts) {
      $scope.accounts = accounts;
    });

    $scope.stage.tags = $scope.stage.tags || {};
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'gce';
  });
