'use strict';

describe('Jenkins Execution Details Controller:', function () {
  const angular = require('angular');
  var controller, $scope;

  beforeEach(
    window.module(
      require('./jenkinsExecutionDetails.controller.js')
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, $timeout) {
    this.initializeController = function(stage) {
      var scope = $rootScope.$new();
      scope.stage = stage;
      controller = $controller('JenkinsExecutionDetailsCtrl', {
        '$scope': scope,
        executionDetailsSectionService: { synchronizeSection: angular.noop, },
      });
      $scope = scope;
      $timeout.flush();
    };
  }));

  describe('getting failure message', function () {

    it('should count number of failing tests', function () {
      var stage = {
        context: {
          buildInfo: {
            testResults: [
              { failCount: 0 },
              { failCount: 3 },
              { failCount: 2 }
            ]
          }
        }
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('5 test(s) failed.');
    });

    it ('should fall back to "build failed" message when no failed tests found, but result is "FAILURE"', function () {
      var stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: []
          }
        }
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('Build failed.');

      stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: [ { failCount: 0 }]
          }
        }
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('Build failed.');
    });

    it ('should fall back to "no reason provided" message when not failing', function () {
      this.initializeController({});
      expect($scope.failureMessage).toBe('No reason provided.');
    });

  });

});
