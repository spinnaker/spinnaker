'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ClustersNavCtrl', function ($scope, application, _) {

    $scope.application = application;
    $scope.sortField = 'accountName';

    $scope.sortOptions = [
      {
        label: 'Account',
        key: 'accountName',
        getDisplayValue: function(cluster) {
          return cluster.accountName;
        },
        getDisplayLabel: function(cluster) {
          return cluster.accountName;
        }
      },
      {
        label: 'Name',
        key: 'name',
        getDisplayValue: function(cluster) {
          return cluster.name;
        },
        getDisplayLabel: function(cluster) {
          return cluster.name;
        }
      },
      {
        label: 'Region',
        key: 'region',
        getDisplayValue: function(cluster) {
          return _.unique(_.collect(cluster.serverGroups, 'region')).sort();
        },
        getDisplayLabel: function(cluster) {
          return _.unique(_.collect(cluster.serverGroups, 'region')).sort().join(' ');
        }
      }
    ];

    function getSelectedSortOption() {
      return $scope.sortOptions.filter(function(option) {
        return option.key === $scope.sortField;
      })[0];
    }

    $scope.getHeadings = function() {
      var selectedOption = getSelectedSortOption();
      var allValues = application.clusters.map(selectedOption.getDisplayValue);
      return _.unique(_.flatten(allValues)).sort();
    };

    $scope.getClustersFor = function (value) {
      return $scope.clusters.filter(function (cluster) {
        if ($scope.sortField === 'region') {
          return cluster.serverGroups.some(function(serverGroup) {
            return serverGroup.region === value;
          });
        }
        return cluster[$scope.sortField] === value;
      });
    };

    $scope.getClusterLabel = function(cluster) {
      if ($scope.sortField === 'name') {
        return cluster.accountName;
      }
      return cluster.name;
    };

    $scope.getClusterSublabel = function(cluster) {
      var labelFields = $scope.sortOptions.filter(function(sortOption) {
        if ($scope.sortField === 'name') {
          return sortOption.key === 'region';
        }
        return sortOption.key !== $scope.sortField && sortOption.key !== 'name';
      });
      return labelFields[0].getDisplayLabel(cluster).toString();
    };

    $scope.clusters = application.clusters;
    $scope.clustersLoaded = true;
  }
);

