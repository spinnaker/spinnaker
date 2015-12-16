'use strict';

let angular = require('angular');

// controllerAs: securityGroupFilters

module.exports = angular.module('securityGroup.filter.controller', [
  require('./securityGroup.filter.service.js'),
  require('./securityGroup.filter.model.js'),
  require('../../utils/lodash.js'),
])
  .controller('SecurityGroupFilterCtrl', function ($scope, app, _, $log, securityGroupFilterService, SecurityGroupFilterModel, $rootScope) {

    $scope.application = app;
    $scope.sortFilter = SecurityGroupFilterModel.sortFilter;

    var ctrl = this;

    this.updateSecurityGroups = function() {
      SecurityGroupFilterModel.applyParamsToUrl();
      securityGroupFilterService.updateSecurityGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.securityGroups, option))).sort();
    }

    function clearFilters() {
      securityGroupFilterService.clearFilters();
      securityGroupFilterService.updateSecurityGroups(app);
    }

    this.initialize = function() {
      ctrl.accountHeadings = getHeadingsForOption('account');
      ctrl.regionHeadings = getHeadingsForOption('region');
      ctrl.stackHeadings = getHeadingsForOption('stack');
      ctrl.providerTypeHeadings = getHeadingsForOption('provider');
      ctrl.clearFilters = clearFilters;
      $scope.securityGroups = app.securityGroups;
    };

    this.initialize();

    app.registerAutoRefreshHandler(this.initialize, $scope);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      SecurityGroupFilterModel.activate();
      securityGroupFilterService.updateSecurityGroups(app);
    }));

  }
);
