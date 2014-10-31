'use strict';


angular.module('deckApp')
  .controller('FeedbackModalCtrl', function($scope, $location, $http, $modalInstance, settings) {

    $scope.states = {
      EDITING: 0,
      SUBMITTING: 1,
      SUBMITTED: 2,
      ERROR: 3
    };

    $scope.state = $scope.states.EDITING;

    $scope.feedback = {
      title: '',
      description: '',
      contact: ''
    };

    function buildDescription() {
      return [
        '**Submitted by:**\n' + $scope.feedback.contact,
        '**From page:**\n' + $location.absUrl(),
        '**Description:**\n' + $scope.feedback.description
      ].join('\n\n');
    }

    function buildRequestBody() {
      return {
        title: $scope.feedback.title,
        description: buildDescription()
      };
    }

    this.submit = function () {
      $scope.state = $scope.states.SUBMITTING;
      $http.post(settings.feedbackUrl, buildRequestBody())
        .success(function(result) {
          $scope.state = $scope.states.SUBMITTED;
          $scope.issueUrl = result.url;
        })
        .error(function() {
          $scope.state = $scope.states.ERROR;
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
