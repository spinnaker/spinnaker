'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.selector', [
  require('../../../../account/account.service.js'),
  require('../../../../utils/lodash.js'),
])
  .directive('preconditionSelector', function() {
    return {
      restrict: 'E',
      scope: {
        precondition: '=',
        level: '=',
        strategy: '=',
      },
      templateUrl: require('./preconditionSelector.html'),
      controller: 'PreconditionSelectorCtrl',
      controllerAs: 'preconditionCtrl'
    };
  })
  .controller('PreconditionSelectorCtrl', function($scope, preconditionTypeService, accountService, _) {
    accountService.listAccounts().then((accounts) => {
      $scope.accounts = accounts;
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

    this.accountUpdated = function () {
      if (!$scope.precondition.context.credentials) {
        return;
      }

      accountService.getRegionsForAccount($scope.precondition.context.credentials).then((regions) => {
        $scope.regions = _.pluck(regions, 'name');
      });
    };

    this.accountUpdated();
  });
