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
      LoadBalancerFilterModel.reconcileDependentFilters(ctrl.regionsKeyedByAccount);
      LoadBalancerFilterModel.applyParamsToUrl();
      loadBalancerFilterService.updateLoadBalancerGroups(app);
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.loadBalancers.data, option))).sort();
    }

    function getAvailabilityZones() {
      var attached = _(app.loadBalancers.data)
        .pluck('instances')
        .flatten()
        .pluck('zone')
        .compact()
        .unique()
        .valueOf(),
        detached = _(app.loadBalancers.data)
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

    this.getAvailabilityZoneHeadings = () => {
      let selectedRegions = LoadBalancerFilterModel.getSelectedRegions();
      let availableRegions = this.getRegionHeadings();

      return selectedRegions.length === 0 ?
        ctrl.availabilityZoneHeadings.filter(zoneFilter(availableRegions)) :
        ctrl.availabilityZoneHeadings.filter(zoneFilter(_.intersection(availableRegions, selectedRegions)));
    };

    this.getRegionHeadings = () => {
      let selectedAccounts = LoadBalancerFilterModel.sortFilter.account;

      return Object.keys(_.pluck(selectedAccounts, _.identity)).length === 0 ?
        ctrl.regionHeadings :
        _(ctrl.regionsKeyedByAccount)
          .filter((regions, account) => account in selectedAccounts)
          .flatten()
          .uniq()
          .valueOf();
    };

    function zoneFilter(regions) {
      return function (azName) {
        return regions.reduce((matches, region) => {
          return matches ? matches : _.includes(azName, region);
        }, false);
      };
    }

    function getRegionsKeyedByAccount() {
      return _(app.loadBalancers.data)
        .groupBy('account')
        .mapValues((loadBalancers) => _(loadBalancers).pluck('region').uniq().valueOf())
        .valueOf();
    }

    this.initialize = function() {
      ctrl.accountHeadings = getHeadingsForOption('account');
      ctrl.regionsKeyedByAccount = getRegionsKeyedByAccount();
      ctrl.regionHeadings = getHeadingsForOption('region');
      ctrl.stackHeadings = ['(none)'].concat(getHeadingsForOption('stack'));
      ctrl.providerTypeHeadings = getHeadingsForOption('type');
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.clearFilters = clearFilters;
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
