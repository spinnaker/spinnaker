'use strict';

let angular = require('angular');

// controllerAs: clustersFilters

module.exports = angular.module('cluster', [
  require('./clusterFilterService.js'),
  require('./clusterFilterModel.js'),
  require('utils/lodash.js'),
])
  .controller('ClusterFilterCtr', function ($scope, app, _, $stateParams, $log, clusterFilterService, ClusterFilterModel) {

    $scope.application = app;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var ctrl = this;

    var accountOption = {
      getDisplayValue: function(serverGroup) {
        return serverGroup.account || '';
      },
    };

    var regionOption = {
      getDisplayValue: function(serverGroup) {
        return serverGroup.region || '';
      },
    };

    var providerType = {
      getDisplayValue: function (serverGroup) {
        return serverGroup.type || '';
      }
    };

    var instanceType = {
      getDisplayValue: function (serverGroup) {
        return serverGroup.instanceType || '';
      }
    };


    this.updateClusterGroups = _.debounce(function updateClusterGroups() {
      clusterFilterService.updateQueryParams();
      $scope.$evalAsync(
        clusterFilterService.updateClusterGroups(app)
      );
    }, 300);

    function getHeadingsForOption(option) {
      var allValues = app.serverGroups.map(option.getDisplayValue);
      return _.compact(_.unique(_.flatten(allValues))).sort();
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
      ctrl.accountHeadings = getHeadingsForOption(accountOption);
      ctrl.regionHeadings = getHeadingsForOption(regionOption);
      ctrl.instanceTypeHeadings = getHeadingsForOption(instanceType);
      ctrl.providerTypeHeadings = getHeadingsForOption(providerType);
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.clearFilters = clearFilters;
      $scope.clusters = app.clusters;
    };

    this.initialize();

    app.registerAutoRefreshHandler(this.initialize, $scope);

  }
)
.name;

