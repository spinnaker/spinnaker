'use strict';

import { module } from 'angular';
import { first, isNil } from 'lodash';

import { AccountService } from '../../../../account/AccountService';
import { AppListExtractor } from '../../../../application/listExtractor/AppListExtractor';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_SELECTOR_PRECONDITIONSELECTOR_DIRECTIVE =
  'spinnaker.core.pipeline.config.preconditions.selector';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_SELECTOR_PRECONDITIONSELECTOR_DIRECTIVE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_SELECTOR_PRECONDITIONSELECTOR_DIRECTIVE, [])
  .directive('preconditionSelector', function () {
    return {
      restrict: 'E',
      scope: {
        precondition: '=',
        level: '=',
        strategy: '=',
        application: '=',
        upstreamStages: '=',
      },
      templateUrl: require('./preconditionSelector.html'),
      controller: 'PreconditionSelectorCtrl',
      controllerAs: 'preconditionCtrl',
    };
  })
  .controller('PreconditionSelectorCtrl', [
    '$scope',
    'preconditionTypeService',
    function ($scope, preconditionTypeService) {
      AccountService.listAccounts().then((accounts) => {
        $scope.accounts = accounts;
        setClusterList();
      });
      $scope.preconditionTypes = preconditionTypeService.listPreconditionTypes();
      $scope.regions = [];

      $scope.precondition = $scope.precondition || { failPipeline: true };
      $scope.precondition.context = $scope.precondition.context || {};
      if (!$scope.precondition.type && $scope.preconditionTypes && $scope.preconditionTypes.length) {
        $scope.precondition.type = $scope.preconditionTypes[0].key;
      }

      this.clearContext = function () {
        $scope.precondition.context = null;
      };

      this.setContext = function (context) {
        // Called from React component
        $scope.$applyAsync(() => {
          $scope.precondition.context = context;
        });
      };

      this.getPreconditionContextTemplateUrl = function () {
        const preconditionConfig = preconditionTypeService.getPreconditionType($scope.precondition.type);
        return preconditionConfig ? preconditionConfig.contextTemplateUrl : '';
      };

      this.clusterChanged = function (clusterName) {
        const clusterFilter = AppListExtractor.monikerClusterNameFilter(clusterName);
        const moniker = first(AppListExtractor.getMonikers([$scope.application], clusterFilter));
        if (!isNil(moniker)) {
          //cluster monikers dont have sequences
          moniker.sequence = undefined;
        }
        $scope.precondition.context.moniker = moniker;
      };

      const setClusterList = () => {
        const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
          $scope.precondition.context.credentials,
          $scope.precondition.context.regions,
        );
        $scope.clusterList = AppListExtractor.getClusters([$scope.application], clusterFilter);
      };

      this.resetSelectedCluster = () => {
        $scope.precondition.context.cluster = undefined;
        $scope.precondition.context.moniker = undefined;
        setClusterList();
      };

      this.accountUpdated = function () {
        if (!$scope.precondition.context.credentials) {
          return;
        }

        const accountFilter = (cluster) =>
          cluster ? cluster.account === $scope.precondition.context.credentials : true;
        $scope.regions = AppListExtractor.getRegions([$scope.application], accountFilter);

        //Setting cloudProvider when account is updated
        const providerFilter = (account) => account.name === $scope.precondition.context.credentials;
        const accountsArray = $scope.accounts;
        if (accountsArray !== null && accountsArray !== undefined) {
          const account = accountsArray.find(providerFilter);
          if (account !== null && account !== undefined) {
            $scope.precondition.cloudProvider = account.type;
          }
        }
      };

      this.reset = () => {
        this.accountUpdated();
        this.resetSelectedCluster();
      };

      this.accountUpdated();
    },
  ]);
