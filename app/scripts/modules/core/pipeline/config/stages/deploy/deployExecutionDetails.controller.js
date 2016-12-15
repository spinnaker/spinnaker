'use strict';

import moment from 'moment';
import _ from 'lodash';
import detailsSectionModule from 'core/delivery/details/executionDetailsSection.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';
import {URL_BUILDER_SERVICE} from 'core/navigation/urlBuilder.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy.details.controller', [
  require('angular-ui-router'),
  require('core/cluster/filter/clusterFilter.service.js'),
  detailsSectionModule,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  URL_BUILDER_SERVICE,
  require('core/cloudProvider/cloudProvider.registry'),
  NAMING_SERVICE,
])
  .controller('DeployExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService,
                                                      urlBuilderService, clusterFilterService, cloudProviderRegistry, namingService) {

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
              cloudProvider: context.providerType || context.cloudProvider || 'aws',
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
      configureWaitingMessages(results);
      $scope.provider = context.cloudProvider || context.providerType || 'aws';
    };

    function configureWaitingMessages(deployedArtifacts) {
      $scope.showWaitingMessage = false;
      $scope.waitingForUpInstances = false;
      $scope.showScalingActivitiesLink = false;
      $scope.showPlatformHealthOverrideMessage = false;

      if (!deployedArtifacts.length) {
        return;
      }
      const deployed = deployedArtifacts[0];
      const stage = $scope.stage;

      const activeWaitTask = (stage.tasks || []).find(t => ['RUNNING', 'TERMINAL'].includes(t.status) && t.name === 'waitForUpInstances');

      if (activeWaitTask && stage.context.lastCapacityCheck) {
        $scope.showWaitingMessage = true;
        $scope.waitingForUpInstances = activeWaitTask.status === 'RUNNING';
        const lastCapacity = stage.context.lastCapacityCheck;
        const waitDurationExceeded = activeWaitTask.runningTimeInMs > moment.duration(5, 'minutes').asMilliseconds();
        lastCapacity.total = lastCapacity.up + lastCapacity.down + lastCapacity.outOfService + lastCapacity.unknown + lastCapacity.succeeded + lastCapacity.failed;

        if (cloudProviderRegistry.getValue(stage.context.cloudProvider, 'serverGroup.scalingActivitiesEnabled')) {
          // after three minutes, if desired capacity is less than total number of instances,
          // show the scaling activities link
          if (waitDurationExceeded && lastCapacity.total < stage.context.capacity.desired) {
            $scope.showScalingActivitiesLink = true;
            $scope.scalingActivitiesTarget = {
              name: deployed.serverGroup,
              app: deployed.application,
              account: deployed.account,
              region: deployed.region,
              cluster: namingService.getClusterNameFromServerGroupName(deployed.serverGroup),
              cloudProvider: deployed.cloudProvider,
            };
          }
        }
        // Also show platform health warning after three minutes if instances are in an unknown state
        if (waitDurationExceeded &&
          stage.context.lastCapacityCheck.unknown > 0 &&
          stage.context.lastCapacityCheck.unknown === stage.context.lastCapacityCheck.total &&
          !stage.context.interestingHealthProviderNames &&
          !_.get($scope.application.attributes, 'platformHealthOverride', false)) {
            $scope.showPlatformHealthOverrideMessage = true;
        }
      }
    }

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
    if (_.hasIn($scope.application, 'executions.onRefresh')) {
      $scope.application.executions.onRefresh($scope, initialize);
    }

  });
