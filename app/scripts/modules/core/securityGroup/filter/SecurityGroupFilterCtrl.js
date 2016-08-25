'use strict';

let angular = require('angular');

// controllerAs: securityGroupFilters

module.exports = angular.module('securityGroup.filter.controller', [
  require('./securityGroup.filter.service.js'),
  require('./securityGroup.filter.model.js'),
  require('../../utils/lodash.js'),
  require('../../filterModel/dependentFilter/dependentFilter.service.js'),
  require('./securityGroupDependentFilterHelper.service.js'),
])
  .controller('SecurityGroupFilterCtrl', function ($scope, app, _, $log, securityGroupFilterService,
                                                   SecurityGroupFilterModel, $rootScope,
                                                   dependentFilterService, securityGroupDependentFilterHelper) {

    $scope.application = app;
    $scope.sortFilter = SecurityGroupFilterModel.sortFilter;

    var ctrl = this;

    this.updateSecurityGroups = function() {
      let { account, region } = dependentFilterService.digestDependentFilters({
        sortFilter: SecurityGroupFilterModel.sortFilter,
        dependencyOrder: ['providerType', 'account', 'region'],
        pool: securityGroupDependentFilterHelper.poolBuilder(app.securityGroups.data)
      });
      ctrl.accountHeadings = account;
      ctrl.regionHeadings = region;

      SecurityGroupFilterModel.applyParamsToUrl();
      securityGroupFilterService.updateSecurityGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.securityGroups.data, option))).sort();
    }

    function clearFilters() {
      securityGroupFilterService.clearFilters();
      securityGroupFilterService.updateSecurityGroups(app);
      ctrl.updateSecurityGroups();
    }

    this.initialize = function() {
      ctrl.stackHeadings = ['(none)'].concat(getHeadingsForOption('stack'));
      ctrl.providerTypeHeadings = getHeadingsForOption('provider');
      ctrl.clearFilters = clearFilters;
      ctrl.updateSecurityGroups();
    };

    this.initialize();

    app.serverGroups.onRefresh($scope, this.initialize);
    app.loadBalancers.onRefresh($scope, this.initialize);
    app.securityGroups.onRefresh($scope, this.initialize);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      SecurityGroupFilterModel.activate();
      securityGroupFilterService.updateSecurityGroups(app);
    }));

  }
);
