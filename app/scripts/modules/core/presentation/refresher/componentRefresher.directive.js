'use strict';

const angular = require('angular');

require('./componentRefresher.directive.less');

module.exports = angular
  .module('spinnaker.core.presentation.refresher.directive', [

  ])
  .directive('componentRefresher', function () {
    return {
      restrict: 'E',
      templateUrl: require('./componentRefresher.directive.html'),
      scope: {},
      bindToController: {
        state: '=',
        templateUrl: '=',
        refresh: '&',
      },
      controller: 'ComponentRefresherCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ComponentRefresherCtrl', function($window) {

    this.$window = $window;

    this.getAgeColor = () => {
      const yellowAge = 2 * 60 * 1000; // 2 minutes
      const redAge = 5 * 60 * 1000; // 5 minutes
      let lastRefresh = this.state.lastRefresh || 0;
      let age = new Date().getTime() - lastRefresh;

      return age < yellowAge ? 'young' :
        age < redAge ? 'old' : 'ancient';
    };
  });
