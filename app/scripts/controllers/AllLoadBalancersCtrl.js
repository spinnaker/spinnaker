'use strict';

angular.module('deckApp')
  .controller('AllLoadBalancersCtrl', function($scope, application, _) {
    $scope.application = application;

    $scope.sortFilter = {
      sortPrimary: 'account',
      filter: '',
      showAsgs: true,
      showAllInstances: false,
      hideHealthy: false
    };

    $scope.sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Region', key: 'region' }
    ];

    $scope.asgOptions = [
      { label: 'All ASGs', key: 'all'},
      { label: 'All ASGs, with instances', key: 'instances'},
      { label: 'Only ASGs with unhealthy instances', key: 'unhealthy' }
    ];

    function addSearchField(loadBalancers) {
      loadBalancers.forEach(function(loadBalancer) {
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

    function matchesFilter(filter, loadBalancer) {
      return filter.every(function (testWord) {
        return loadBalancer.searchField.indexOf(testWord) !== -1;
      });
    }

    function filterLoadBalancersForDisplay(loadBalancers, hideHealthy, filter) {
      return loadBalancers.filter(function (loadBalancer) {
        if (hideHealthy) {
          var hasUnhealthy = loadBalancer.serverGroups.some(function (serverGroup) {
            return serverGroup.downCount > 0;
          });
          if (!hasUnhealthy) {
            return false;
          }
        }
        if (!filter.length) {
          return true;
        }
        return matchesFilter(filter, loadBalancer);
      });
    }

    function updateLoadBalancerGroups() {
      var loadBalancers = application.loadBalancers;
      var groups = [],
        filter = $scope.sortFilter.filter ? $scope.sortFilter.filter.toLowerCase().split(' ') : [],
        primarySort = $scope.sortFilter.sortPrimary,
        secondarySort = $scope.sortOptions.filter(function(option) { return option.key !== primarySort; })[0].key,
        hideHealthy = $scope.sortFilter.hideHealthy;

      addSearchField(loadBalancers);

      var filtered = filterLoadBalancersForDisplay(loadBalancers, hideHealthy, filter);

      var grouped = _.groupBy(filtered, primarySort);

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, secondarySort),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          subGroups.push( { heading: subKey, subgroups: _.sortBy(subGroup, 'name') } );
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
      });
      $scope.groups = _.sortBy(groups, 'heading');
      $scope.$digest(); // debounced
    }

    $scope.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    $scope.updateLoadBalancerGroups();

  });
