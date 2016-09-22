'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.nav.controller', [])
  .controller('LoadBalancersNavCtrl', function ($scope, app) {

    $scope.application = app;
    $scope.loadBalancers = app.loadBalancers;

    $scope.sortField = 'account';

    $scope.sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Name', key: 'name' },
      { label: 'Region', key: 'region' }
    ];

    this.getHeadings = function getHeadings() {
      var allValues = _.map(app.loadBalancers, $scope.sortField);
      return _.compact(_.uniq(allValues)).sort();
    };

    this.getLoadBalancersFor = function getLoadBalancersFor(value) {
      return app.loadBalancers.data.filter(function (loadBalancer) {
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
