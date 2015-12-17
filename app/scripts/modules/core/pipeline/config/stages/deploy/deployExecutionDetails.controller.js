'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy.details.controller', [
  require('../../../../utils/lodash.js'),
  require('angular-ui-router'),
  require('../../../../cluster/filter/clusterFilter.service.js'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../navigation/urlBuilder.service.js'),
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService, $timeout, urlBuilderService, clusterFilterService) {

    $scope.configSections = ['deploymentConfig', 'taskStatus'];

    if ($scope.stage.context) {
      if ($scope.stage.context.commits && $scope.stage.context.commits.length > 0) {
        $scope.configSections.push('codeChanges');
      }
      if (!_.isEmpty($scope.stage.context.jarDiffs)) {
        $scope.configSections.push('JARChanges');
      }
    }

    function initialize() {

      executionDetailsSectionService.synchronizeSection($scope.configSections);

      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the deployed artifacts
      $timeout(function () {
        var context = $scope.stage.context || {},
          results = [];

        function addDeployedArtifacts(key) {
          var deployedArtifacts = _.find(resultObjects, key);
          if (deployedArtifacts) {
            _.forEach(deployedArtifacts[key], function (serverGroupName, region) {
              var result = {
                type: 'serverGroups',
                application: context.application,
                serverGroup: serverGroupName,
                account: context.account,
                region: region,
                provider: context.providerType || context.cloudProvider || 'aws'
              };
              result.href = urlBuilderService.buildFromMetadata(result);
              results.push(result);
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
        $scope.provider = context.cloudProvider || context.providerType || 'aws';
      });
    }

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
