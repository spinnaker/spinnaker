'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.baseProviderStage', [
  require('../../../../utils/lodash.js'),
  require('../../../../config/settings'),
  require('../../../../cloudProvider/providerSelection/providerSelector.directive.js'),
])
  .controller('BaseProviderStageCtrl', function($scope, stage, accountService, pipelineConfig, _, settings) {

    // Docker Bake is wedged in here because it doesn't really fit our existing cloud provider paradigm
    let dockerBakeEnabled = _.get(settings, 'feature.dockerBake') && stage.type === 'bake';

    $scope.stage = stage;

    $scope.viewState = $scope.viewState || {};
    $scope.viewState.loading = true;

    var stageProviders = pipelineConfig.getProvidersFor(stage.type);

    if (dockerBakeEnabled) {
      stageProviders.push({cloudProvider: 'docker'});
    }

    accountService.listProviders($scope.application).then(function (providers) {
      $scope.viewState.loading = false;
      var availableProviders = _.intersection(providers, _.pluck(stageProviders, 'cloudProvider'));
      if (dockerBakeEnabled) {
        availableProviders.push('docker');
      }
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

  });

