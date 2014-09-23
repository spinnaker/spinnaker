'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application, $modal, searchService, mortService,
                                          securityGroupService, accountService, oortService,
                                          _, $stateParams, $location) {
    var defPrimary = 'cluster', defSecondary = 'region';
    $scope.sortFilter.allowSorting = true;
    $scope.sortFilter.sortPrimary = $stateParams.primary || defPrimary;
    $scope.sortFilter.sortSecondary = $stateParams.secondary || defSecondary;
    $scope.sortFilter.filter = $stateParams.q || '';
    $scope.sortFilter.showAllInstances = ($stateParams.hideInstances ? false : true);
    $scope.sortFilter.hideHealthy = ($stateParams.hideHealthy === 'true');
    $scope.sortFilter.hideDisabled = ($stateParams.hideDisabled === 'true');

    var sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Cluster Name', key: 'cluster' },
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
    }.bind(this);

    $scope.$watch('sortFilter.sortPrimary', this.updateSorting);

    function checkAgainstActiveFilters(serverGroup) {
      return [
        $scope.sortFilter.hideHealthy && serverGroup.downCount === 0,
        $scope.sortFilter.hideDisabled && serverGroup.isDisabled,
      ].some(function(x) {
        return x;
      }) ? false : true;
    }

    function addSearchFields() {
      application.clusters.forEach(function(cluster) {
        cluster.serverGroups.forEach(function(serverGroup) {
          if (!serverGroup.searchField) {
            serverGroup.searchField = [
              serverGroup.region.toLowerCase(),
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

    function updateQueryParams() {
      $location.search('q',
        $scope.sortFilter.filter.length > 0 ? $scope.sortFilter.filter : null);
      $location.search('hideHealthy', $scope.sortFilter.hideHealthy ? true : null);
      $location.search('hideInstances', $scope.sortFilter.showAllInstances ? null : true);
      $location.search('hideDisabled', $scope.sortFilter.hideDisabled ? true : null);
      $location.search('primary',
        $scope.sortFilter.sortPrimary===defPrimary ? null:$scope.sortFilter.sortPrimary);
      $location.search('secondary',
        $scope.sortFilter.sortSecondary===defSecondary ? null:$scope.sortFilter.sortSecondary);
    }

    function updateClusterGroups() {
      updateQueryParams();
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

    this.createServerGroup = function createServerGroup() {
      $modal.open({
        templateUrl: 'views/modal/asgWizard.html',
        controller: 'CloneServerGroupCtrl as ctrl',
        resolve: {
          title: function() { return 'Create New Server Group'; },
          application: function() { return application; },
          serverGroup: function() { return null; }
        }
      });
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    application.onAutoRefresh = function() {
      addSearchFields();
      updateClusterGroups();
    };

    application.onAutoRefresh();

  }
);
