'use strict';

angular.module('deckApp.delivery')
  .controller('executionDetails', function($scope, $stateParams, $state) {
    var controller = this;

    controller.close = function() {
      $state.go('home.applications.application.executions', {
        application: $stateParams.application,
      });
    };
  });

