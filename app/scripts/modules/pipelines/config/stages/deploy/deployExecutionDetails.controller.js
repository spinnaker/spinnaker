'use strict';

angular.module('deckApp.pipelines.stage.deploy.details.controller', [
  'deckApp.utils.lodash',
  'ui.router',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService, $timeout) {

    $scope.configSections = ['deploymentConfig', 'taskStatus'];

    function initialize() {

      executionDetailsSectionService.synchronizeSection($scope.configSections);

      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the deployed artifacts
      $timeout(function () {
        var context = $scope.stage.context,
          results = [];

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          var resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            results = [];
            var deployedArtifacts = _.find(resultObjects, 'serverGroupNameByRegion');
            if (deployedArtifacts) {
              _.forEach(deployedArtifacts.serverGroupNameByRegion, function (serverGroupName, region) {
                results.push({
                  region: region,
                  name: serverGroupName,
                  provider: context.provider || 'aws',
                });
              });
            }
          }
        }
        $scope.deployed = results;
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
