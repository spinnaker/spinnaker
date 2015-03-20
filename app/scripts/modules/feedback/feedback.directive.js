'use strict';

angular.module('deckApp.feedback.directive', [])
  .directive('feedback', function() {
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
          console.warn('gottle eh', $scope.state.showMenu);
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
