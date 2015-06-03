'use strict';

angular.module('spinnaker.pipelines.stage.deploy.details.controller', [
  'spinnaker.utils.lodash',
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService, $timeout) {

    $scope.configSections = ['deploymentConfig', 'taskStatus', 'codeChanges'];

    function initialize() {

      executionDetailsSectionService.synchronizeSection($scope.configSections);

      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the deployed artifacts
      $timeout(function () {
        var context = $scope.stage.context,
          results = [];

        function addDeployedArtifacts(key) {
          var deployedArtifacts = _.find(resultObjects, key);
          if (deployedArtifacts) {
            _.forEach(deployedArtifacts[key], function (serverGroupName, region) {
              results.push({
                region: region,
                name: serverGroupName,
                provider: context.provider || 'aws',
              });
            });
          }
        }

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          var resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            results = [];
            addDeployedArtifacts('asgNameByRegion'); // TODO: Remove after 5/11/15
            addDeployedArtifacts('serverGroupNameByRegion');
          }
        }
        $scope.deployed = results;
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
