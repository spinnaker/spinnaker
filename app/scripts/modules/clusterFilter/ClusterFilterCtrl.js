'use strict';

// controllerAs: clustersFilters

angular.module('cluster', ['cluster.filter.service', 'cluster.filter.model', 'deckApp.utils.lodash'])
  .controller('ClusterFilterCtr', function ($scope, application, _, $stateParams, $log, clusterFilterService, ClusterFilterModel) {

    $scope.sortFilter.sortPrimary = 'account';
    $scope.sortFilter.sortSecondary = 'cluster';
    $scope.application = application;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var ctrl = this;

    var accountOption = {
      label: 'Account',
      key: 'account',
      clusterKey: 'accountName',
      getDisplayValue: function(cluster) {
        return cluster.account || '';
      },
      getDisplayLabel: function(cluster) {
        return cluster.account || '';
      }
    };

    var regionOption = {
      label: 'Region',
      key: 'region',
      clusterKey: 'region',
      getDisplayValue: function(cluster) {
        return _.unique(_.collect(cluster.serverGroups, 'region')).sort();
      },
      getDisplayLabel: function(cluster) {
        return _.unique(_.collect(cluster.serverGroups, 'region')).sort().join(' ');
      }
    };

    var providerType = {
      getDisplayValue: function (cluster) {
        return _.unique(_.collect(cluster.serverGroups, 'type'));
      }
    };

    var instanceType = {
      getDisplayValue: function (cluster) {
        return _.unique(_.collect(cluster.serverGroups, 'instanceType'));
      }
    };


    this.updateClusterGroups = _.debounce(function updateClusterGroups() {
      clusterFilterService.updateQueryParams();
      $scope.$evalAsync(
        clusterFilterService.updateClusterGroups(application)
      );
    }, 300);

    function getSelectedSortOption() {
      return $scope.sortOptions.filter(function(option) {
        return option.key === $scope.sortFilter.sortPrimary;
      })[0];
    }

    this.getHeadings = function getHeadings() {
      var selectedOption = getSelectedSortOption();
      var allValues = application.clusters.map(selectedOption.getDisplayValue);
      return _.compact(_.unique(_.flatten(allValues))).sort();
    };


    function getAccountHeadings() {
      var accountNameList = getHeadingsForOption(accountOption);
      return accountNameList;
    }

    function getRegionHeadings() {
      return getHeadingsForOption(regionOption);
    }

    function getProviderType() {
      return getHeadingsForOption(providerType);
    }

    function getInstanceType() {
      return getHeadingsForOption(instanceType);
    }

    function getHeadingsForOption(option) {
      var allValues = application.clusters.map(option.getDisplayValue);
      return _.compact(_.unique(_.flatten(allValues))).sort();
    }

    function getAvailabilityZones() {
      return _(application.serverGroups)
        .pluck('instances')
        .flatten()
        .pluck('availabilityZone')
        .unique()
        .valueOf();
    }

    function clearFilters() {
      clusterFilterService.clearFilters();
      clusterFilterService.updateClusterGroups(application);
    }

    this.initialize = function() {
      ctrl.accountHeadings = getAccountHeadings();
      ctrl.regionHeadings = getRegionHeadings();
      ctrl.instanceTypeHeadings = getInstanceType();
      ctrl.providerTypeHeadings = getProviderType();
      ctrl.availabilityZoneHeadings = getAvailabilityZones();
      ctrl.clearFilters = clearFilters;
      $scope.clusters = application.clusters;
    };

    this.initialize();

    application.registerAutoRefreshHandler(this.initialize, $scope);

  }
);

