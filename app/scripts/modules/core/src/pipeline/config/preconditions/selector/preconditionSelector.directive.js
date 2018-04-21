'use strict';

const angular = require('angular');
import { AccountService } from 'core/account/AccountService';
import { LIST_EXTRACTOR_SERVICE } from 'core/application/listExtractor/listExtractor.service';
import { isNil, first } from 'lodash';

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.selector', [LIST_EXTRACTOR_SERVICE])
  .directive('preconditionSelector', function() {
    return {
      restrict: 'E',
      scope: {
        precondition: '=',
        level: '=',
        strategy: '=',
        application: '=',
      },
      templateUrl: require('./preconditionSelector.html'),
      controller: 'PreconditionSelectorCtrl',
      controllerAs: 'preconditionCtrl',
    };
  })
  .controller('PreconditionSelectorCtrl', function($scope, preconditionTypeService, appListExtractorService) {
    AccountService.listAccounts().then(accounts => {
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

    this.clearContext = function() {
      $scope.precondition.context = null;
    };

    this.getPreconditionContextTemplateUrl = function() {
      var preconditionConfig = preconditionTypeService.getPreconditionType($scope.precondition.type);
      return preconditionConfig ? preconditionConfig.contextTemplateUrl : '';
    };

    this.clusterChanged = function(clusterName) {
      let clusterFilter = appListExtractorService.monikerClusterNameFilter(clusterName);
      let moniker = first(appListExtractorService.getMonikers([$scope.application], clusterFilter));
      if (!isNil(moniker)) {
        //cluster monikers dont have sequences
        moniker.sequence = undefined;
      }
      $scope.precondition.context.moniker = moniker;
    };

    let setClusterList = () => {
      let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion(
        $scope.precondition.context.credentials,
        $scope.precondition.context.regions,
      );
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    this.resetSelectedCluster = () => {
      $scope.precondition.context.cluster = undefined;
      $scope.precondition.context.moniker = undefined;
      setClusterList();
    };

    this.accountUpdated = function() {
      if (!$scope.precondition.context.credentials) {
        return;
      }

      let accountFilter = cluster => (cluster ? cluster.account === $scope.precondition.context.credentials : true);
      $scope.regions = appListExtractorService.getRegions([$scope.application], accountFilter);

      //Setting cloudProvider when account is updated
      let providerFilter = account => account.name === $scope.precondition.context.credentials;
      let accountsArray = $scope.accounts;
      if (accountsArray !== null && accountsArray !== undefined) {
        let account = accountsArray.find(providerFilter);
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
  });
