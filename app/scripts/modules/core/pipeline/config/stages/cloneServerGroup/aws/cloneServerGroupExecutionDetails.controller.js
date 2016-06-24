'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cloneServerGroup.aws.executionDetails.controller', [
  require('../../../../../utils/lodash.js'),
  require('angular-ui-router'),
  require('../../../../../cluster/filter/clusterFilter.service.js'),
  require('../../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../../navigation/urlBuilder.service.js'),
])
  .controller('awsCloneServerGroupExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, $timeout, urlBuilderService, clusterFilterService) {

    $scope.configSections = ['cloneServerGroupConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the deployed artifacts
      $timeout(() => {
        let context = $scope.stage.context || {},
          results = [];

        function addDeployedArtifacts(key) {
          let deployedArtifacts = _.find(resultObjects, key);
          if (deployedArtifacts) {
            _.forEach(deployedArtifacts[key], (serverGroupNameAndRegion) => {
              if (serverGroupNameAndRegion.includes(':')) {
                let [region, serverGroupName] = serverGroupNameAndRegion.split(':');
                let result = {
                  type: 'serverGroups',
                  application: context.application,
                  serverGroup: serverGroupName,
                  account: context.credentials,
                  region: region,
                  provider: 'aws',
                  project: $stateParams.project,
                };
                result.href = urlBuilderService.buildFromMetadata(result);
                results.push(result);
              }
            });
          }
        }

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          var resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            addDeployedArtifacts('serverGroupNames');
          }
        }
        $scope.deployed = results;
      });
    }

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
