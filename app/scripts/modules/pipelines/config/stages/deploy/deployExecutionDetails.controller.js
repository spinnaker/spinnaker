'use strict';

angular.module('deckApp.pipelines.stage.deploy.details.controller', [
  'deckApp.utils.lodash',
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, _) {

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
