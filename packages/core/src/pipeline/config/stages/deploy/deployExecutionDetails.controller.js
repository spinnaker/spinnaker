'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import _ from 'lodash';
import { Duration } from 'luxon';

import { CloudProviderRegistry } from '../../../../cloudProvider';
import { EXECUTION_DETAILS_SECTION_SERVICE } from '../../../details/executionDetailsSection.service';
import { HelpContentsRegistry } from '../../../../help';
import { NameUtils } from '../../../../naming';
import { UrlBuilder } from '../../../../navigation';
import { ServerGroupReader } from '../../../../serverGroup/serverGroupReader.service';
import { ClusterState } from '../../../../state';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.core.pipeline.stage.deploy.details.controller';
export const name = CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYEXECUTIONDETAILS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    EXECUTION_DETAILS_SECTION_SERVICE,
  ])
  .controller('DeployExecutionDetailsCtrl', [
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    function ($scope, $stateParams, executionDetailsSectionService) {
      $scope.configSections = ['deploymentConfig', 'taskStatus', 'artifactStatus'];

      function areJarDiffsEmpty() {
        let result = true;

        const jarDiffs = $scope.stage.context.jarDiffs;
        if (!_.isEmpty(jarDiffs)) {
          result = !Object.keys(jarDiffs).some((key) => Array.isArray(jarDiffs[key]) && jarDiffs[key].length);
        }

        return result;
      }

      function evaluateSections() {
        $scope.configSections = ['deploymentConfig', 'taskStatus', 'artifactStatus'];

        if ($scope.stage.context) {
          if (
            ($scope.stage.context.commits && $scope.stage.context.commits.length > 0) ||
            !areJarDiffsEmpty($scope.stage.context.jarDiffs)
          ) {
            $scope.configSections.push('changes');
          }
        }
      }

      evaluateSections();

      const initialized = () => {
        const context = $scope.stage.context || {};
        let resultObjects;
        evaluateSections();
        $scope.detailsSection = $stateParams.details;

        let results = [];

        function addDeployedArtifacts(key) {
          const deployedArtifacts = _.find(resultObjects, key);
          if (deployedArtifacts) {
            _.forEach(deployedArtifacts[key], function (serverGroupName, region) {
              const result = {
                type: 'serverGroups',
                application: context.application,
                serverGroup: serverGroupName,
                account: context.account,
                region: region,
                provider: context.providerType || context.cloudProvider || 'aws',
                cloudProvider: context.providerType || context.cloudProvider || 'aws',
                project: $stateParams.project,
              };
              result.href = UrlBuilder.buildFromMetadata(result);
              results.push(result);
            });
          }
        }

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            results = [];
            addDeployedArtifacts('serverGroupNameByRegion');
          }
        }
        $scope.deployed = results;
        configureWaitingMessages(results);
        $scope.provider = context.cloudProvider || context.providerType || 'aws';

        $scope.changeConfig = {
          buildInfo: context.buildInfo || {},
          commits: $scope.stage.context.commits,
          jarDiffs: $scope.stage.context.jarDiffs,
        };

        $scope.customStuckDeployGuide = HelpContentsRegistry.getHelpField('execution.stuckDeploy.guide');

        if (_.has(context, 'source.region') && context['deploy.server.groups']) {
          const serverGroupName = context['deploy.server.groups'][context.source.region][0];
          ServerGroupReader.getServerGroup(context.application, context.account, context.source.region, serverGroupName)
            .then((serverGroup) => {
              if (_.has(serverGroup, 'buildInfo.jenkins')) {
                $scope.changeConfig.buildInfo.jenkins = serverGroup.buildInfo.jenkins;
              }
            })
            .catch(() => {});
        }
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

        const activeWaitTask = (stage.tasks || []).find(
          (t) => ['RUNNING', 'TERMINAL'].includes(t.status) && t.name === 'waitForUpInstances',
        );

        if (activeWaitTask && stage.context.lastCapacityCheck) {
          $scope.showWaitingMessage = true;
          $scope.waitingForUpInstances = activeWaitTask.status === 'RUNNING';
          const lastCapacity = stage.context.lastCapacityCheck;
          const waitDurationExceeded =
            activeWaitTask.runningTimeInMs > Duration.fromObject({ minutes: 5 }).as('milliseconds');
          lastCapacity.total =
            lastCapacity.up +
            lastCapacity.down +
            lastCapacity.outOfService +
            lastCapacity.unknown +
            lastCapacity.succeeded +
            lastCapacity.failed;

          if (CloudProviderRegistry.getValue(stage.context.cloudProvider, 'serverGroup.scalingActivitiesEnabled')) {
            // after three minutes, if desired capacity is less than total number of instances,
            // show the scaling activities link
            if (waitDurationExceeded && lastCapacity.total < stage.context.capacity.desired) {
              $scope.showScalingActivitiesLink = true;
              $scope.scalingActivitiesTarget = {
                name: deployed.serverGroup,
                app: deployed.application,
                account: deployed.account,
                region: deployed.region,
                cluster: NameUtils.getClusterNameFromServerGroupName(deployed.serverGroup),
                cloudProvider: deployed.cloudProvider,
              };
            }
          }
          // Also show platform health warning after three minutes if instances are in an unknown state
          if (
            waitDurationExceeded &&
            stage.context.lastCapacityCheck.unknown > 0 &&
            stage.context.lastCapacityCheck.unknown === stage.context.lastCapacityCheck.total &&
            !stage.context.interestingHealthProviderNames &&
            !_.get($scope.application.attributes, 'platformHealthOverride', false)
          ) {
            $scope.showPlatformHealthOverrideMessage = true;
          }
        }
      }

      this.overrideFiltersForUrl = (r) => ClusterState.filterService.overrideFiltersForUrl(r);

      const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
      if (_.hasIn($scope.application, 'executions.onRefresh')) {
        $scope.application.executions.onRefresh($scope, initialize);
      }
    },
  ]);
