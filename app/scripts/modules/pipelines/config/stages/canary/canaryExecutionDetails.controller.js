'use strict';

angular.module('deckApp.pipelines.stage.canary.details.controller', [
  'deckApp.utils.lodash',
])
  .controller('CanaryExecutionDetailsCtrl', function ($scope, _) {

    var context = $scope.stage.context,
      results = [];

    if (context && context['kato.tasks'] && context['kato.tasks'].length) {
      var resultObjects = context['kato.tasks'][0].resultObjects;
      if (resultObjects && resultObjects.length) {
        results = [];
        var deployedArtifacts = _.find(resultObjects, 'asgNameByRegion');
        if (deployedArtifacts) {
          _.forEach(deployedArtifacts.asgNameByRegion, function (asgName, region) {
            results.push({
              region: region,
              name: asgName,
            });
          });
        }
      }
    }
    $scope.deployed = results;

  });
