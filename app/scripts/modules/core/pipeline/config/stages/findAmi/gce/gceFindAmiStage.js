'use strict';

let angular = require('angular');

//BEN_TODO
module.exports = angular.module('spinnaker.core.pipeline.stage.gce.findAmiStage', [
  require('../../../../../utils/lodash.js'),
  require('./findAmiExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'findImage',
      cloudProvider: 'gce',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection'},
        { type: 'requiredField', fieldName: 'zones' },
        { type: 'requiredField', fieldName: 'credentials' }
      ]
    });
  }).controller('gceFindAmiStageCtrl', function($scope, accountService, _) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      zonesLoaded: false
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.zones = {"us-central1": ['us-central1-a', 'us-central1-b', 'us-central1-c']};

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(zoneMap) {
        $scope.zones = zoneMap;
        $scope.zonesLoaded = true;
      });
    };

    $scope.selectionStrategies = [{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple server groups exist, prefer the server group with the most instances'
    }, {
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple server groups exist, prefer the newest'
    }, {
      label: 'Oldest',
      val: 'OLDEST',
      description: 'When multiple server groups exist, prefer the oldest'
    }, {
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple server groups exist, fail'
    }];

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'gce';
    stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

    if (angular.isUndefined(stage.onlyEnabled)) {
      stage.onlyEnabled = true;
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }
    if (!stage.zones.length && $scope.application.defaultRegions.gce) {
      stage.zones.push($scope.application.defaultRegions.gce);
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

