'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application, _) {

    $scope.sortFilter = {
      sortPrimary: 'cluster',
      sortSecondary: 'region',
      filter: ''
    };

    var sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Cluster', key: 'cluster' },
      { label: 'Region', key: 'region' }
    ];

    $scope.sortOptions = function(exclude) {
      return exclude ?
        sortOptions.filter(function(option) { return option.key !== exclude; }) :
        sortOptions;
    };

    $scope.updateSorting = function() {
      var sortFilter = $scope.sortFilter;
      if (sortFilter.sortPrimary === sortFilter.sortSecondary) {
        sortFilter.sortSecondary = $scope.sortOptions(sortFilter.sortPrimary)[0].key;
      }
      $scope.updateClusterGroups();
    };

    function updateClusterGroups() {
      var groups = [],
        totalInstancesDisplayed = 0,
        filter = $scope.sortFilter.filter.toLowerCase(),
        primarySort = $scope.sortFilter.sortPrimary,
        secondarySort = $scope.sortFilter.sortSecondary,
        tertiarySort = sortOptions.filter(function(option) { return option.key !== primarySort && option.key !== secondarySort; })[0].key;

      var serverGroups = _.chain(application.clusters)
        .collect('serverGroups')
        .flatten()
        .filter(function(serverGroup) {
          if (!filter) {
            return true;
          }
          if (!serverGroup.searchField) {
            serverGroup.searchField = [
              serverGroup.region.toLowerCase(),
              serverGroup.name.toLowerCase(),
              serverGroup.account.toLowerCase(),
                _.collect(serverGroup.loadBalancers, 'name').join(' ')
            ].join(' ');
          }
          return filter.split(' ').every(function(testWord) {
            return serverGroup.searchField.indexOf(testWord) !== -1;
          });
        })
        .value();

      var grouped = _.groupBy(serverGroups, primarySort);

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, secondarySort),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subGroupings = _.groupBy(subGroup, tertiarySort),
            subSubGroups = [];

          _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
            totalInstancesDisplayed += subGroup.reduce(function (total, asg) {
              return asg.instances.length + total;
            }, 0);
            subSubGroups.push( { heading: subSubKey, serverGroups: subSubGroup } );
          });
          subGroups.push( { heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading') } );
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
      });

      $scope.totalInstancesDisplayed = totalInstancesDisplayed;
      $scope.renderInstancesOnScroll = totalInstancesDisplayed > 2000; // TODO: Move to config

      $scope.groups = _.sortBy(groups, 'heading');
      $scope.$digest(); // downside of debouncing

    }

    $scope.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    $scope.updateClusterGroups();
    $scope.clustersLoaded = true;

  }
);
