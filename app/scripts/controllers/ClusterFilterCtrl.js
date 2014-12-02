'use strict';

// controllerAs: clustersFilters

angular.module('cluster', ['cluster.filter.service', 'cluster.filter.model'])
  .controller('ClusterFilterCtr', function ($scope, application, _, $stateParams, $log, ClusterFilterService, clusterFilterModel) {
    var defaultPrimary = 'account';
    $scope.sortFilter.sortPrimary = $stateParams.primary || defaultPrimary;

    $scope.application = application;

    var accountOption = {
      label: 'Account',
      key: 'account',
      clusterKey: 'accountName',
      getDisplayValue: function(cluster) {
        return cluster.accountName || '';
      },
      getDisplayLabel: function(cluster) {
        return cluster.accountName || '';
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

    var clusterOption = {
      label: 'Cluster Name',
      key: 'cluster',
      clusterKey: 'name',
      getDisplayValue: function (cluster) {
        return cluster.name;
      },
      getDisplayLabel: function (cluster) {
        return cluster.name;
      }
    };

    $scope.sortOptions = [
      accountOption,
      clusterOption,
      regionOption
    ];

    $scope.sortFilter = clusterFilterModel;

    this.updateClusterGroups = function updateClusterGroups() {
      ClusterFilterService.updateQueryParams();
      ClusterFilterService.updateClusterGroups(application);
    };

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

    function getHeadingsForOption(option) {
      var allValues = application.clusters.map(option.getDisplayValue);
      return _.compact(_.unique(_.flatten(allValues))).sort();
    }

    this.getAccountHeadings = function getAccountHeadings() {
      return getHeadingsForOption(accountOption);
    };

    this.getRegionHeadings = function getRegionHeadings() {
      return getHeadingsForOption(regionOption);
    };


    this.getClustersFor = function getClustersFor(value) {
      return application.clusters.filter(function (cluster) {
        if ($scope.sortFilter.sortPrimary === 'region') {
          return cluster.serverGroups.some(function(serverGroup) {
            return serverGroup.region === value;
          });
        }
        return cluster.serverGroups &&
          cluster.serverGroups.length > 0 &&
          cluster[getSelectedSortOption().clusterKey] === value;
      });
    };

    this.getClusterLabel = function getClusterLabel(cluster) {
      if ($scope.sortFilter.sortPrimary === 'cluster') {
        return cluster.accountName;
      }
      return cluster.name;
    };

    this.getClusterSublabel = function getClusterSublabel(cluster) {
      var labelFields = $scope.sortOptions.filter(function(sortOption) {
        if ($scope.sortFilter.sortPrimary === 'cluster') {
          return sortOption.key === 'region';
        }
        return sortOption.key !== $scope.sortFilter.sortPrimary && sortOption.key !== 'cluster';
      });
      return labelFields[0].getDisplayLabel(cluster).toString();
    };

    $scope.clusters = application.clusters;

  }
);

