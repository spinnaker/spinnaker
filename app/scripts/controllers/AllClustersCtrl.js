'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application, _, $filter) {

    $scope.sortFilter = {
      allowSorting: true,
      sortPrimary: 'cluster',
      sortSecondary: 'region',
      filter: '',
      showAllInstances: true,
      hideHealthy: false,
      hideDisabled: false,
    };

    var sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Cluster', key: 'cluster' },
      { label: 'Region', key: 'region' }
    ];

    this.getSortOptions = function getSortOptions(exclude) {
      return exclude ?
        sortOptions.filter(function(option) { return option.key !== exclude; }) :
        sortOptions;
    };

    this.updateSorting = function updateSorting() {
      var sortFilter = $scope.sortFilter;
      if (sortFilter.sortPrimary === sortFilter.sortSecondary) {
        sortFilter.sortSecondary = this.getSortOptions(sortFilter.sortPrimary)[0].key;
      }
      this.updateClusterGroups();
    };

    function checkAgainstActiveFilters(serverGroup) {
      return [
        $scope.sortFilter.hideHealthy && serverGroup.downCount == 0,
        $scope.sortFilter.hideDisabled && serverGroup.isDisabled,
      ].any(function(x) {
        return x;
      }) ? false : true;
    }

    function addSearchFields() {
      application.clusters.forEach(function(cluster) {
        cluster.serverGroups.forEach(function(serverGroup) {
          if (!serverGroup.searchField) {
            serverGroup.searchField = [
              $filter('regionAbbreviator')(serverGroup.region).toLowerCase(),
              serverGroup.name.toLowerCase(),
              serverGroup.account.toLowerCase(),
              _.collect(serverGroup.loadBalancers, 'name').join(' '),
              _.collect(serverGroup.instances, 'instanceId').join(' ')
            ].join(' ');
          }
        });
      });
    }

    function filterServerGroupsForDisplay(serverGroups, filter) {
      return  _.chain(application.clusters)
        .collect('serverGroups')
        .flatten()
        .filter(function(serverGroup) {
          if (!filter) {
            return true;
          }
          return filter.split(' ').every(function(testWord) {
            return serverGroup.searchField.indexOf(testWord) !== -1;
          });
        })
        .filter(checkAgainstActiveFilters)
        .value();
    }

    function incrementTotalInstancesDisplayed(totalInstancesDisplayed, serverGroups) {
      return serverGroups
        .filter(checkAgainstActiveFilters)
        .reduce(function(total, serverGroup) {
          return serverGroup.asg.instances.length + total;
        }, totalInstancesDisplayed);
    }

    function updateClusterGroups() {
      $scope.$evalAsync(function() {
        var groups = [],
          totalInstancesDisplayed = 0,
          filter = $scope.sortFilter.filter.toLowerCase(),
          primarySort = $scope.sortFilter.sortPrimary,
          secondarySort = $scope.sortFilter.sortSecondary,
          tertiarySort = sortOptions.filter(function(option) { return option.key !== primarySort && option.key !== secondarySort; })[0].key;

        var serverGroups = filterServerGroupsForDisplay(application.serverGroups, filter);

        var grouped = _.groupBy(serverGroups, primarySort);

        _.forOwn(grouped, function(group, key) {
          var subGroupings = _.groupBy(group, secondarySort),
            subGroups = [];

          _.forOwn(subGroupings, function(subGroup, subKey) {
            var subGroupings = _.groupBy(subGroup, tertiarySort),
              subSubGroups = [];

            _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
              totalInstancesDisplayed = incrementTotalInstancesDisplayed(totalInstancesDisplayed, subSubGroup);
              subSubGroups.push( { heading: subSubKey, serverGroups: subSubGroup } );
            });
            subGroups.push( { heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading') } );
          });

          groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
        });

        $scope.groups = _.sortBy(groups, 'heading');

        $scope.displayOptions = {
          renderInstancesOnScroll: totalInstancesDisplayed > 2000, // TODO: move to config
          showInstances: $scope.sortFilter.showAllInstances,
          hideHealthy: $scope.sortFilter.hideHealthy,
          filter: $scope.sortFilter.filter
        };
      });
    }

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    application.onAutoRefresh = function() {
      addSearchFields();
      updateClusterGroups();
    };

    application.onAutoRefresh();

  }
);
