'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.nav.controller', [
 require('../utils/lodash.js')
])
  .controller('LoadBalancersNavCtrl', function ($scope, app, _) {

    $scope.application = app;
    $scope.loadBalancers = app.loadBalancers;

    $scope.sortField = 'account';

    $scope.sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Name', key: 'name' },
      { label: 'Region', key: 'region' }
    ];

    this.getHeadings = function getHeadings() {
      var allValues = _.collect(app.loadBalancers, $scope.sortField);
      return _.compact(_.unique(allValues)).sort();
    };

    this.getLoadBalancersFor = function getLoadBalancersFor(value) {
      return app.loadBalancers.filter(function (loadBalancer) {
        return loadBalancer[$scope.sortField] === value;
      });
    };

    this.getLoadBalancerLabel = function getLoadBalancerLabel(loadBalancer) {
      if ($scope.sortField === 'name') {
        return loadBalancer.account;
      }
      return loadBalancer.name;
    };

    this.getLoadBalancerSublabel = function getLoadBalancerSublabel(loadBalancer) {
      var labelFields = $scope.sortOptions.filter(function(sortOption) {
        if ($scope.sortField === 'name') {
          return sortOption.key === 'region';
        }
        return sortOption.key !== $scope.sortField && sortOption.key !== 'name';
      });
      return loadBalancer[labelFields[0].key];
    };
  }
);
