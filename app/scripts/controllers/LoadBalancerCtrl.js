'use strict';

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function($scope, application, loadBalancerName, _, sortingService) {
    $scope.application = application;
    $scope.loadBalancerName = loadBalancerName;

    application.getLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers.filter(function(loadBalancer) {
        return loadBalancer.name === loadBalancerName;
      });
    });

    $scope.sortFilter = {
      sortPrimary: 'name',
      sortSecondary: 'account',
      filter: ''
    };

    var sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Load Balancer', key: 'name' },
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
      $scope.updateLoadBalancerGroups();
    };

    function addServerGroupsAndSearchFields(loadBalancers, clusters) {
      loadBalancers.forEach(function (loadBalancer) {
        if (!loadBalancer.serverGroups) {
          loadBalancer.serverGroups = [];
          var clusterMatches = clusters.filter(function (cluster) {
            return cluster.account === loadBalancer.account;
          });
          clusterMatches.forEach(function (matchedCluster) {
            matchedCluster.serverGroups.forEach(function (serverGroup) {
              if (serverGroup.region === loadBalancer.region && loadBalancer.serverGroupNames.indexOf(serverGroup.name) !== -1) {
                loadBalancer.serverGroups.push(serverGroup);
              }
            });
          });
        }
        if (!loadBalancer.searchField) {
          loadBalancer.searchField = [
            loadBalancer.name,
            loadBalancer.region,
            loadBalancer.account,
            loadBalancer.serverGroupNames.join(' ')
          ].join(' ');
        }
      });
    }

    function updateLoadBalancerGroups() {
      application.getClusters().then(function(clusters) {
        var loadBalancers = $scope.loadBalancers,
          groups = [],
          filteredInstanceCount = 0,
          filter = $scope.sortFilter.filter.toLowerCase(),
          primarySort = $scope.sortFilter.sortPrimary,
          secondarySort = $scope.sortFilter.sortSecondary,
          tertiarySort = sortOptions.filter(function(option) { return option.key !== primarySort && option.key !== secondarySort; })[0].key;

        addServerGroupsAndSearchFields(loadBalancers, clusters);

        var filtered = loadBalancers.filter(function(loadBalancer) {
          if (!filter) {
            return true;
          }
          return filter.split(' ').every(function(testWord) {
            return loadBalancer.searchField.indexOf(testWord) !== -1;
          });
        });

        var grouped = _.groupBy(filtered, primarySort);

        _.forOwn(grouped, function(group, key) {
          var subGroupings = _.groupBy(group, secondarySort),
            subGroups = [];

          _.forOwn(subGroupings, function(subGroup, subKey) {
            var subGroupings = _.groupBy(subGroup, tertiarySort),
              subSubGroups = [];

            _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
              var serverGroups = _.flatten(_.collect(subSubGroup, 'serverGroups'));
              filteredInstanceCount = serverGroups.reduce(function(memo, serverGroup) {
                return memo + serverGroup.instances.length;
              }, filteredInstanceCount);
              subSubGroups.push( { type: tertiarySort, heading: subSubKey, serverGroups: serverGroups.sort(sortingService.asgSorter) } );
            });
            subGroups.push( { type: secondarySort, heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading') } );
          });

          groups.push( { type: primarySort, heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
        });
        $scope.filteredInstanceCount = filteredInstanceCount;
        console.warn(filteredInstanceCount);
        $scope.groups = _.sortBy(groups, 'heading');
      });
    }

    $scope.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    updateLoadBalancerGroups();
  });
