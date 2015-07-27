'use strict';

angular.module('spinnaker.feedback.directive', [])
  .directive('feedback', function($location) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/feedback/feedback.html',
      controller: function($scope, $modal) {

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
          $modal.open({
            templateUrl: 'scripts/modules/feedback/feedback.modal.html',
            controller: 'FeedbackModalCtrl as ctrl'
          });
        };
      }
    };
  });
