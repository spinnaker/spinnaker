'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.baseProviderStage', [
  require('../../../../utils/lodash.js'),
  require('../../../../providerSelection/providerSelector.directive.js'),
])
  .controller('BaseProviderStageCtrl', function($scope, stage, accountService, pipelineConfig, _) {

    $scope.stage = stage;

    $scope.viewState = $scope.viewState || {};
    $scope.viewState.loading = true;

    var stageProviders = pipelineConfig.getProvidersFor(stage.type);

    accountService.listProviders().then(function (providers) {
      $scope.viewState.loading = false;
      var availableProviders = _.intersection(providers, _.pluck(stageProviders, 'cloudProvider'));
      if (availableProviders.length === 1) {
        $scope.stage.cloudProviderType = availableProviders[0];
      } else {
        $scope.providers = availableProviders;
      }
    });

    function loadProvider() {
      var stageProvider = _.find(stageProviders, { cloudProvider: stage.cloudProviderType });
      if (stageProvider) {
        $scope.stage.type = stageProvider.key || $scope.stage.type;
        $scope.providerStageDetailsUrl = stageProvider.templateUrl;
      }
    }

    $scope.$watch('stage.cloudProviderType', loadProvider);

  })
  .name;

