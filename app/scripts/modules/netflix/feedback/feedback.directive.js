'use strict';

require('./feedback.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.feedback.directive', [
  require('../../core/config/settings')
])
  .directive('feedback', function($location, settings) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./feedback.html'),
      controller: function($scope, $uibModal) {

        $scope.slackConfig = settings.feedback ? settings.feedback.slack : null;

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
