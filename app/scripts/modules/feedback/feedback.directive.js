'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.feedback.directive', [
])
  .directive('feedback', function() {
    return {
      restrict: 'E',
      templateUrl: require('./feedback.html'),
      controller: function($scope, $modal) {

        $scope.state = {
          showMenu: false,
          isMac: navigator.platform.toLowerCase().indexOf('mac') !== -1,
        };

        $scope.toggleMenu = function() {
          $scope.state.showMenu = !$scope.state.showMenu;
        };

        $scope.openFeedback = function() {
          $modal.open({
            templateUrl: require('./feedback.modal.html'),
            controller: 'FeedbackModalCtrl as ctrl'
          });
        };
      }
    };
  });
