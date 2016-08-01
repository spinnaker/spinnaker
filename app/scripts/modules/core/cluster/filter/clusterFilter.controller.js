'use strict';

let angular = require('angular');

// controllerAs: clustersFilters

module.exports = angular.module('cluster', [
  require('./collapsibleFilterSection.directive.js'),
  require('./clusterFilter.service.js'),
  require('./clusterFilter.model.js'),
  require('../../utils/lodash.js'),
])
  .controller('ClusterFilterCtrl', function ($scope, app, _, $log, clusterFilterService, ClusterFilterModel, $rootScope) {

    $scope.application = app;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var ctrl = this;

    this.updateClusterGroups = () => {
      ClusterFilterModel.reconcileDependentFilters(ctrl.regionsKeyedByAccount);
      ClusterFilterModel.applyParamsToUrl();
      clusterFilterService.updateClusterGroups(app);
    };

    ctrl.getAvailabilityZoneHeadings = () => {
      let selectedRegions = ClusterFilterModel.getSelectedRegions();
      let availableRegions = ctrl.getRegionHeadings();

      return selectedRegions.length === 0 ?
        ctrl.availabilityZoneHeadings.filter(zoneFilter(availableRegions)) :
        ctrl.availabilityZoneHeadings.filter(zoneFilter(_.intersection(availableRegions, selectedRegions)));
    };

    function zoneFilter(regions) {
      return function (azName) {
        return regions.reduce((matches, region) => {
          return matches ? matches : _.includes(azName, region);
        }, false);
      };
    }

    ctrl.getRegionHeadings = () => {
      let selectedAccounts = ClusterFilterModel.sortFilter.account;

      return Object.keys(_.pick(selectedAccounts, _.identity)).length === 0 ?
        ctrl.regionHeadings :
        _(ctrl.regionsKeyedByAccount)
          .filter((regions, account) => account in selectedAccounts)
          .flatten()
          .uniq()
          .valueOf();
    };


    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.serverGroups.data, option))).sort();
    }

    function getAvailabilityZones() {
      return _(app.serverGroups.data)
        .pluck('instances')
        .flatten()
        .pluck('availabilityZone')
        .unique()
        .valueOf();
    }

    function clearFilters() {
      clusterFilterService.clearFilters();
      clusterFilterService.updateClusterGroups(app);
    }

    function getRegionsKeyedByAccount() {
      return _(app.serverGroups.data)
        .groupBy('account')
        .mapValues((instances) => _(instances).pluck('region').uniq().valueOf())
        .valueOf();
    }

    this.initialize = function() {
      ctrl.accountHeadings = getHeadingsForOption('account');
      ctrl.regionsKeyedByAccount = getRegionsKeyedByAccount();
      ctrl.regionHeadings = getHeadingsForOption('region');
      ctrl.instanceTypeHeadings = getHeadingsForOption('instanceType');
      ctrl.providerTypeHeadings = getHeadingsForOption('type');
      ctrl.stackHeadings = ['(none)'].concat(getHeadingsForOption('stack'));
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.categoryHeadings = getHeadingsForOption('category');
      ctrl.clearFilters = clearFilters;
      $scope.clusters = app.clusters;
    };


    if (app.serverGroups.loaded) {
      this.initialize();
    }

    app.serverGroups.onRefresh($scope, this.initialize);

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      ClusterFilterModel.activate();
      clusterFilterService.updateClusterGroups(app);
    }));
  }
);
