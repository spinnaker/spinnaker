'use strict';

import _ from 'lodash';
import detailsSectionModule from '../../../../delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy.details.controller', [
  require('angular-ui-router'),
  require('../../../../cluster/filter/clusterFilter.service.js'),
  detailsSectionModule,
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../navigation/urlBuilder.service.js'),
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, urlBuilderService, clusterFilterService) {

    $scope.configSections = ['deploymentConfig', 'taskStatus'];

    if ($scope.stage.context) {
      if ($scope.stage.context.commits && $scope.stage.context.commits.length > 0) {
        $scope.configSections.push('codeChanges');
      }
      if (!_.isEmpty($scope.stage.context.jarDiffs)) {
        $scope.configSections.push('JARChanges');
      }
    }

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;

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
              provider: context.providerType || context.cloudProvider || 'aws',
              project: $stateParams.project,
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
          addDeployedArtifacts('serverGroupNameByRegion');
        }
      }
      $scope.deployed = results;
      $scope.provider = context.cloudProvider || context.providerType || 'aws';
    };

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
