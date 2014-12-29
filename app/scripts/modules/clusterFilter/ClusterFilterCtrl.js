'use strict';

// controllerAs: clustersFilters

angular.module('cluster', ['cluster.filter.service', 'cluster.filter.model', 'deckApp.utils.lodash'])
  .controller('ClusterFilterCtr', function ($scope, application, _, $stateParams, $log, clusterFilterService, ClusterFilterModel) {

    var defaultPrimary = 'account';
    $scope.sortFilter.sortPrimary = $stateParams.primary || defaultPrimary;
    $scope.application = application;
    $scope.sortFilter = ClusterFilterModel.sortFilter;

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

    $scope.sortOptions = [
      accountOption,
      clusterOption,
      regionOption
    ];


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

    function clearFilters() {
      clusterFilterService.clearFilters();
      clusterFilterService.updateClusterGroups(application);
    }


    this.accountHeadings = getAccountHeadings();
    this.regionHeadings = getRegionHeadings();
    this.instanceTypeHeadings = getInstanceType();
    this.providerTypeHeadings = getProviderType();
    this.clearFilters = clearFilters;

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

    $scope.clusters = application.clusters;

  }
);

