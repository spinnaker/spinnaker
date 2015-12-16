'use strict';

let angular = require('angular');

// controllerAs: loadBalancerFilters

module.exports = angular.module('spinnaker.core.loadBalancer.filter.controller', [
  require('./loadBalancer.filter.service.js'),
  require('./loadBalancer.filter.model.js'),
  require('../../utils/lodash.js'),
])
  .controller('LoadBalancerFilterCtrl', function ($scope, app, _, $log, loadBalancerFilterService, LoadBalancerFilterModel, $rootScope) {

    $scope.application = app;
    $scope.sortFilter = LoadBalancerFilterModel.sortFilter;

    var ctrl = this;

    this.updateLoadBalancerGroups = () => {
      LoadBalancerFilterModel.applyParamsToUrl();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.loadBalancers, option))).sort();
    }

    function getAvailabilityZones() {
      var attached = _(app.loadBalancers)
        .pluck('instances')
        .flatten()
        .pluck('zone')
        .compact()
        .unique()
        .valueOf(),
        detached = _(app.loadBalancers)
          .pluck('detachedInstances')
          .flatten()
          .pluck('zone')
          .compact()
          .unique()
          .valueOf();

      return _.uniq(attached.concat(detached));
    }

    function clearFilters() {
      loadBalancerFilterService.clearFilters();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    }

    this.initialize = function() {
      ctrl.accountHeadings = getHeadingsForOption('account');
      ctrl.regionHeadings = getHeadingsForOption('region');
      ctrl.stackHeadings = getHeadingsForOption('stack');
      ctrl.providerTypeHeadings = getHeadingsForOption('type');
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.clearFilters = clearFilters;
      $scope.loadBalancers = app.loadBalancers;
    };

    this.initialize();

    app.registerAutoRefreshHandler(this.initialize, $scope);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      LoadBalancerFilterModel.activate();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    }));
  }
);
