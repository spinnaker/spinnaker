'use strict';

describe('Jenkins Execution Details Controller:', function () {
  var $scope;

  beforeEach(window.module(require('./jenkinsExecutionDetails.controller').name));

  beforeEach(
    window.inject(function ($controller, $rootScope) {
      this.initializeController = function (stage) {
        var scope = $rootScope.$new();
        scope.stage = stage;
        $controller('JenkinsExecutionDetailsCtrl', {
          $scope: scope,
          executionDetailsSectionService: { synchronizeSection: (a, fn) => fn() },
        });
        $scope = scope;
      };
    }),
  );

  describe('getting failure message', function () {
    it('should count number of failing tests', function () {
      var stage = {
        context: {
          buildInfo: {
            testResults: [{ failCount: 0 }, { failCount: 3 }, { failCount: 2 }],
          },
        },
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('5 test(s) failed.');
    });

    it('should fall back to "build failed" message when no failed tests found, but result is "FAILURE"', function () {
      var stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: [],
          },
        },
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('Build failed.');

      stage = {
        context: {
          buildInfo: {
            result: 'FAILURE',
            testResults: [{ failCount: 0 }],
          },
        },
      };

      this.initializeController(stage);

      expect($scope.failureMessage).toBe('Build failed.');
    });

    it('should set failureMessage to undefined when not failing', function () {
      this.initializeController({});
      expect($scope.failureMessage).toBeUndefined();
    });
  });
});
