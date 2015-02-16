'use strict';

angular.module('deckApp.feedback.directive', [])
  .directive('feedback', function() {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/feedback/feedback.html',
      controller: function($scope, $modal) {
        $scope.openFeedback = function() {
          $modal.open({
            templateUrl: 'scripts/modules/feedback/feedback.modal.html',
            controller: 'FeedbackModalCtrl as ctrl'
          });
        };
      }
    };
  });
