'use strict';

describe('Basic Settings Mixin Controller:', function () {
  var controller, $scope;

  beforeEach(window.module(require('./basicSettingsMixin.controller').name));

  beforeEach(
    window.inject(function ($controller, $rootScope) {
      $scope = $rootScope.$new();
      $scope.application = { name: 'app', serverGroups: [] };
      $scope.command = { viewState: {} };
      controller = $controller('BasicSettingsMixin', {
        $scope: $scope,
        $uibModalStack: { dismissAll: angular.noop },
      });
    }),
  );

  describe('pattern testing: templating not enabled', function () {
    beforeEach(function () {
      $scope.command.viewState = {};
    });

    it('stack should accept underscores, letters, numbers, and nothing', function () {
      var test = controller.stackPattern.test;
      expect(test('a')).toBe(true);
      expect(test('ab')).toBe(true);
      expect(test('a_b')).toBe(true);
      expect(test('9')).toBe(true);
      expect(test('9aA')).toBe(true);
      expect(test(null)).toBe(true);
      expect(test('')).toBe(true);
    });

    it('stack should fail on dashes or various other special characters', function () {
      var test = controller.stackPattern.test;
      expect(test('-a')).toBe(false);
      expect(test('a-')).toBe(false);
      expect(test('-')).toBe(false);
      expect(test('$')).toBe(true);
      expect(test('9*')).toBe(false);
      expect(test('${a}')).toBe(true);
    });

    it('detail should accept underscores, letters, numbers, dashes, and nothing', function () {
      var test = controller.detailPattern.test;
      expect(test('a')).toBe(true);
      expect(test('ab')).toBe(true);
      expect(test('a_b')).toBe(true);
      expect(test('9')).toBe(true);
      expect(test('9aA')).toBe(true);
      expect(test(null)).toBe(true);
      expect(test('')).toBe(true);
      expect(test('-a')).toBe(true);
      expect(test('a-')).toBe(true);
      expect(test('-')).toBe(true);
    });

    it('detail should fail on various special characters', function () {
      var test = controller.detailPattern.test;
      expect(test('$')).toBe(true);
      expect(test('9*')).toBe(false);
      expect(test('#9')).toBe(false);
      expect(test('1@9')).toBe(false);
      expect(test('${a}')).toBe(true);
    });
  });

  describe('pattern testing: templating enabled', function () {
    beforeEach(function () {
      $scope.command.viewState = { templatingEnabled: true };
    });

    it('unfortunately is greedy and accepts invalid placeholders', function () {
      expect(controller.stackPattern.test('${not valid - trailing closing curly brackets}}}')).toBe(true);
      expect(controller.detailPattern.test('${not valid - trailing closing curly brackets}}}')).toBe(true);
    });

    it('stack should accept underscores, letters, numbers, and nothing', function () {
      var test = controller.stackPattern.test;
      expect(test('a')).toBe(true);
      expect(test('ab')).toBe(true);
      expect(test('a_b')).toBe(true);
      expect(test('9')).toBe(true);
      expect(test('9aA')).toBe(true);
      expect(test(null)).toBe(true);
      expect(test('')).toBe(true);
    });

    it('stack should accept template placeholders', function () {
      var test = controller.stackPattern.test;
      expect(test('${a}')).toBe(true);
      expect(test('b${a}')).toBe(true);
      expect(test('${a}b')).toBe(true);
      expect(test('c${a}b')).toBe(true);
      expect(test('c${a}b${d}')).toBe(true);
      expect(test('c_${a}b${d}')).toBe(true);

      expect(test('c-${a}b${d}')).toBe(true);
    });

    it('detail should accept underscores, letters, numbers, dashes, and nothing', function () {
      var test = controller.detailPattern.test;
      expect(test('a')).toBe(true);
      expect(test('ab')).toBe(true);
      expect(test('a_b')).toBe(true);
      expect(test('9')).toBe(true);
      expect(test('9aA')).toBe(true);
      expect(test(null)).toBe(true);
      expect(test('')).toBe(true);
      expect(test('-a')).toBe(true);
      expect(test('a-')).toBe(true);
      expect(test('-')).toBe(true);
    });

    it('detail should accept template placeholders', function () {
      var test = controller.detailPattern.test;
      expect(test('${a}')).toBe(true);
      expect(test('b${a}')).toBe(true);
      expect(test('${a}b')).toBe(true);
      expect(test('c${a}b')).toBe(true);
      expect(test('c-${a}b${d}')).toBe(true);
      expect(test('c_${a}b${d}')).toBe(true);
    });
  });
});
