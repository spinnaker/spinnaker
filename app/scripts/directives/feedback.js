'use strict';

angular.module('deckApp')
  .directive('feedback', function() {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/feedback.html',
      controller: function($scope, $modal) {
        $scope.openFeedback = function() {
          $modal.open({
            templateUrl: 'views/modal/feedback.html',
            controller: 'FeedbackModalCtrl as ctrl'
          });
        };
      }
    };
  });
