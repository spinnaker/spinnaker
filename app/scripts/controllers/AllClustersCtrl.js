'use strict';

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application, _) {

    $scope.sortFilter = {
      sortPrimary: 'region',
      sortSecondary: 'cluster',
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

    $scope.updateClusterGroups = function() {
      var groups = [],
          filter = $scope.sortFilter.filter.toLowerCase(),
          serverGroups = _.chain(application.data.clusters)
            .collect('serverGroups')
            .flatten()
            .filter(function(serverGroup) {
              if (!filter) {
                return true;
              }
              var toCheck = [
                serverGroup.region.toLowerCase(),
                serverGroup.name.toLowerCase(),
                serverGroup.account.toLowerCase(),
                _.collect(serverGroup.instances, 'name').join(' ')
              ].join(' ');
              return toCheck.indexOf(filter) !== -1;
            })
            .value(),

          primarySort = $scope.sortFilter.sortPrimary,
          secondarySort = $scope.sortFilter.sortSecondary,
          tertiarySort = sortOptions.filter(function(option) { return option.key !== primarySort && option.key !== secondarySort; })[0].key;

      var grouped = _.groupBy(serverGroups, primarySort);

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, secondarySort),
            subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subGroupings = _.groupBy(subGroup, tertiarySort),
              subSubGroups = [];

          _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
            subSubGroups.push( { heading: subSubKey, serverGroups: subSubGroup.sort(asgSorter) } );
          });
          subGroups.push( { heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading') } );
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
      });
      $scope.groups = _.sortBy(groups, 'heading');
      console.warn('groups:', $scope.groups);
    };

    function asgSorter(a, b) {
      var av = a.name.split('-').pop(),
        bv = b.name.split('-').pop();
      if (av.indexOf('v') === -1 || bv.indexOf('v') === -1 || isNaN(av.substring(1)) || isNaN(bv.substring(1))) {
        return av - bv;
      } else {
        return parseInt(av.substring(1)) - parseInt(bv.substring(1));
      }
    }

    if (application.data.clusters.length) {
      $scope.updateClusterGroups();
    } else {
      $scope.$on('clustersLoaded', $scope.updateClusterGroups);
    }

  });
