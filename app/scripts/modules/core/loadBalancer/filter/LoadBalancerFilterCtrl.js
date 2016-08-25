'use strict';

let angular = require('angular');

// controllerAs: loadBalancerFilters

module.exports = angular.module('spinnaker.core.loadBalancer.filter.controller', [
  require('./loadBalancer.filter.service.js'),
  require('./loadBalancer.filter.model.js'),
  require('../../utils/lodash.js'),
  require('../../filterModel/dependentFilter/dependentFilter.service.js'),
  require('./loadBalancerDependentFilterHelper.service.js'),
])
  .controller('LoadBalancerFilterCtrl', function ($scope, app, _, $log, loadBalancerFilterService,
                                                  LoadBalancerFilterModel, $rootScope,
                                                  loadBalancerDependentFilterHelper, dependentFilterService) {

    $scope.application = app;
    $scope.sortFilter = LoadBalancerFilterModel.sortFilter;

    var ctrl = this;

    this.updateLoadBalancerGroups = () => {
      let { availabilityZone, region, account } = dependentFilterService.digestDependentFilters({
        sortFilter: LoadBalancerFilterModel.sortFilter,
        dependencyOrder: ['providerType', 'account', 'region', 'availabilityZone'],
        pool: loadBalancerDependentFilterHelper.poolBuilder(app.loadBalancers.data)
      });

      ctrl.accountHeadings = account;
      ctrl.regionHeadings = region;
      ctrl.availabilityZoneHeadings = availabilityZone;

      LoadBalancerFilterModel.applyParamsToUrl();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.loadBalancers.data, option))).sort();
    }

    function clearFilters() {
      loadBalancerFilterService.clearFilters();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
      ctrl.updateLoadBalancerGroups();
    }

    this.initialize = function() {
      ctrl.stackHeadings = ['(none)'].concat(getHeadingsForOption('stack'));
      ctrl.providerTypeHeadings = getHeadingsForOption('type');
      ctrl.clearFilters = clearFilters;
      ctrl.updateLoadBalancerGroups();
    };

    if (app.loadBalancers.loaded) {
      this.initialize();
    }

    app.loadBalancers.onRefresh($scope, this.initialize);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      LoadBalancerFilterModel.activate();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    }));
  }
);
