'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LIST_EXTRACTOR_SERVICE} from 'core/application/listExtractor/listExtractor.service';

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.selector', [
  ACCOUNT_SERVICE,
  LIST_EXTRACTOR_SERVICE,
])
  .directive('preconditionSelector', function() {
    return {
      restrict: 'E',
      scope: {
        precondition: '=',
        level: '=',
        strategy: '=',
        application: '='
      },
      templateUrl: require('./preconditionSelector.html'),
      controller: 'PreconditionSelectorCtrl',
      controllerAs: 'preconditionCtrl'
    };
  })
  .controller('PreconditionSelectorCtrl', function($scope, preconditionTypeService, accountService, appListExtractorService) {
    accountService.listAccounts().then((accounts) => {
      $scope.accounts = accounts;
      setClusterList();
    });
    $scope.preconditionTypes = preconditionTypeService.listPreconditionTypes();
    $scope.regions = [];

    $scope.precondition = $scope.precondition || { failPipeline: true};
    $scope.precondition.context = $scope.precondition.context || {};
    if (!$scope.precondition.type && $scope.preconditionTypes && $scope.preconditionTypes.length) {
      $scope.precondition.type = $scope.preconditionTypes[0].key;
    }

    this.clearContext = function () {
      $scope.precondition.context = null;
    };

    this.getPreconditionContextTemplateUrl = function () {
      var preconditionConfig = preconditionTypeService.getPreconditionType($scope.precondition.type);
      return preconditionConfig ? preconditionConfig.contextTemplateUrl : '';
    };

    let setClusterList = () => {
      let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion($scope.precondition.context.credentials, $scope.precondition.context.regions);
      $scope.clusterList = appListExtractorService.getClusters([$scope.application], clusterFilter);
    };

    this.resetSelectedCluster = () => {
      $scope.precondition.context.cluster = undefined;
      setClusterList();
    };

    this.accountUpdated = function () {
      if (!$scope.precondition.context.credentials) {
        return;
      }

      let accountFilter = (cluster) => cluster ? cluster.account === $scope.precondition.context.credentials : true;
      $scope.regions = appListExtractorService.getRegions([$scope.application], accountFilter);

      //Setting cloudProvider when account is updated
      let providerFilter = (account) => account.name === $scope.precondition.context.credentials;
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
