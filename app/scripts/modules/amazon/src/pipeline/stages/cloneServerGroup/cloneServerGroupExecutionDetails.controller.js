'use strict';

const angular = require('angular');
import _ from 'lodash';

import { CLUSTER_FILTER_SERVICE, URL_BUILDER_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.amazon.pipeline.stage.cloneServerGroup.executionDetails.controller', [
  require('@uirouter/angularjs').default,
  CLUSTER_FILTER_SERVICE,
  URL_BUILDER_SERVICE
])
  .controller('awsCloneServerGroupExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService,
                                                                   urlBuilderService, clusterFilterService) {

    $scope.configSections = ['cloneServerGroupConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

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
    };

    this.overrideFiltersForUrl = r => clusterFilterService.overrideFiltersForUrl(r);

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
