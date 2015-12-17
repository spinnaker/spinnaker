'use strict';
let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.feedback.modal.controller', [
  require('../../core/cache/deckCacheFactory.js'),
  require('../../core/authentication/authentication.service.js'),
  require('../../core/config/settings.js'),
])
  .controller('FeedbackModalCtrl', function($scope, $location, $http, $modalInstance, settings, authenticationService) {

    $scope.states = {
      EDITING: 0,
      SUBMITTING: 1,
      SUBMITTED: 2,
      ERROR: 3
    };

    $scope.state = $scope.states.EDITING;

    $scope.userIsAuthenticated = authenticationService.getAuthenticatedUser().authenticated;

    $scope.feedback = {
      title: '',
      description: '',
      contact: ''
    };

    function getContactInfo() {
      if ($scope.userIsAuthenticated) {
        return authenticationService.getAuthenticatedUser().name;
      }
      return $scope.feedback.contact;
    }

    function getUserNameFromContactInfo() {
      var email = getContactInfo();
      if (email.indexOf('@') !== -1) {
        return email.split('@')[0];
      }
      return email;
    }

    function buildDescription() {
      return [
        '*Submitted by:*\n' + getContactInfo(),
        '*From page:*\n' + $location.absUrl(),
        '*Description:*\n' + $scope.feedback.description
      ].join('\n\n');
    }

    function buildRequestBody() {
      return {
        title: $scope.feedback.title,
        description: buildDescription(),
        contact: getUserNameFromContactInfo(),
      };
    }

    this.submit = function () {
      $scope.state = $scope.states.SUBMITTING;
      $http.post(settings.feedbackUrl, buildRequestBody())
        .success(function(result) {
          $scope.state = $scope.states.SUBMITTED;
          $scope.issueUrl = result.url;
          $scope.issueId = result.id;
        })
        .error(function() {
          $scope.state = $scope.states.ERROR;
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
