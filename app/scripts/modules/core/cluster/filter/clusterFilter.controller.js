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
      ClusterFilterModel.reconcileDependentFilters();
      ClusterFilterModel.applyParamsToUrl();
      clusterFilterService.updateClusterGroups(app);
    };

    ctrl.getAvailabilityZoneHeadings = () => {
      let selectedRegions = ClusterFilterModel.getSelectedRegions();

      return selectedRegions.length === 0 ?
        ctrl.availabilityZoneHeadings :
        ctrl.availabilityZoneHeadings.filter((azName) => {
          return selectedRegions.reduce((matches, region) => {
            return matches ? matches : _.includes(azName, region);
          }, false);
        });
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

    this.initialize = function() {
      ctrl.accountHeadings = getHeadingsForOption('account');
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
