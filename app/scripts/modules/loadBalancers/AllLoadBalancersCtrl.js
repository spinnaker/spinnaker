'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.controller', [
  require('../account/accountService.js'),
  require('../providerSelection/providerSelection.service.js'),
  require('utils/lodash.js'),
  require('../caches/deckCacheFactory.js'),
  require('./LoadBalancerCtrl.js'),
  require('exports?"ui.bootstrap"!angular-bootstrap')
])
  .controller('AllLoadBalancersCtrl', function($scope, $modal, $filter, $q, _, accountService, providerSelectionService, app ) {
    $scope.application = app;

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

    function addSearchField(loadBalancers) {
      loadBalancers.forEach(function(loadBalancer) {
        if (!loadBalancer.searchField) {
          loadBalancer.searchField = [
            loadBalancer.name,
            loadBalancer.region.toLowerCase(),
            loadBalancer.account,
            _.pluck(loadBalancer.serverGroups, 'name').join(' '),
            _.pluck(loadBalancer.instances, 'id').join(' '),
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
          if (loadBalancer.healthCounts.downCount === 0) {
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
      $scope.$evalAsync(function() {
        var loadBalancers = app.loadBalancers,
            groups = [],
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

        $scope.displayOptions = {
          showServerGroups: $scope.sortFilter.showAsgs,
          showInstances: $scope.sortFilter.showAllInstances,
          hideHealthy: $scope.sortFilter.hideHealthy
        };
      });
    }

    this.createLoadBalancer = function createLoadBalancer() {
      //BEN_TODO
      providerSelectionService.selectProvider().then(function(provider) {
        $modal.open({
          templateUrl: 'app/scripts/modules/loadBalancers/configure/' + provider + '/createLoadBalancer.html',
          controller: provider + 'CreateLoadBalancerCtrl as ctrl',
          resolve: {
            application: function() { return app; },
            loadBalancer: function() { return null; },
            isNew: function() { return true; }
          }
        });
      });
    };

    this.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    app.registerAutoRefreshHandler(updateLoadBalancerGroups, $scope);

    updateLoadBalancerGroups();

  }
).name;
