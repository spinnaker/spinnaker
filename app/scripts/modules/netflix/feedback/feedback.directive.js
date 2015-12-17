'use strict';

require('./feedback.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.feedback.directive', [
])
  .directive('feedback', function($location) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./feedback.html'),
      controller: function($scope, $uibModal) {

        $scope.state = {
          showMenu: false,
          isMac: navigator.platform.toLowerCase().indexOf('mac') !== -1,
        };

        $scope.toggleMenu = function() {
          $scope.state.showMenu = !$scope.state.showMenu;
        };

        $scope.getCurrentUrlMessage = function() {
          return encodeURIComponent('(via ' + $location.absUrl() + ')\n');
        };

        $scope.openFeedback = function() {
          $uibModal.open({
            templateUrl: require('./feedback.modal.html'),
            controller: 'FeedbackModalCtrl as ctrl'
          });
        };
      }
    };
  });
