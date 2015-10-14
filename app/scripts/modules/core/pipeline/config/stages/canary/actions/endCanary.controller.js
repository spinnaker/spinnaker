'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.canary.actions.override.result.controller', [
  require('angular-ui-router'),
  require('../../../../../utils/lodash.js'),
  require('../../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('EndCanaryCtrl', function ($scope, $http, $modalInstance, settings, canaryId, _) {

    $scope.command = {
      reason: null,
      result: 'SUCCESS',
    };

    $scope.state = 'editing';

    this.endCanary = function() {
      $scope.state = 'submitting';
      var targetUrl = [settings.gateUrl, 'canaries', canaryId, 'end'].join('/');
      $http.put(targetUrl, $scope.command)
        .success(function() {
          $scope.state = 'success';
        })
        .error(function() {
          $scope.state = 'error';
        });
    };

    this.cancel = $modalInstance.dismiss;

  }).name;
