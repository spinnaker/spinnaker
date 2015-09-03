'use strict';

let angular = require('angular');

// controllerAs: clustersFilters

module.exports = angular.module('cluster', [
  require('./clusterFilterService.js'),
  require('./clusterFilterModel.js'),
  require('../utils/lodash.js'),
])
  .controller('ClusterFilterCtr', function ($scope, app, _, $log, clusterFilterService, ClusterFilterModel) {

    $scope.application = app;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var ctrl = this;

    this.updateClusterGroups = function () {
      ClusterFilterModel.applyParamsToUrl();
      $scope.$evalAsync(
        clusterFilterService.updateClusterGroups(app)
      );
    };

    function getHeadingsForOption(option) {
      return _.compact(_.uniq(_.pluck(app.serverGroups, option))).sort();
    }

    function getAvailabilityZones() {
      return _(app.serverGroups)
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
      ctrl.stackHeadings = getHeadingsForOption('stack');
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.clearFilters = clearFilters;
      $scope.clusters = app.clusters;
    };

    this.initialize();

    app.registerAutoRefreshHandler(this.initialize, $scope);

  }
)
.name;
