'use strict';

import { Registry } from 'core/registry';

require('./overrideTimeout.directive.html');

describe('Directives: overrideTimeout', function() {
  let stageConfig;

  beforeEach(window.module(require('./overrideTimeout.directive').name));

  beforeEach(function() {
    window.inject(function($rootScope, $compile, $controller) {
      this.scope = $rootScope.$new();
      this.scope.stage = {};
      this.compile = $compile;
      this.$controller = $controller;

      stageConfig = { defaultTimeoutMs: 90 * 60 * 1000 };
      Registry.reinitialize();
    });
  });

  describe('checkbox toggle control', function() {
    it('displays nothing when stage is not supported', function() {
      stageConfig = {};
      spyOn(Registry.pipeline, 'getStageConfig').and.returnValue(stageConfig);
      var domNode = this.compile('<override-timeout stage="stage"></override-timeout>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('div').length).toBe(0);
    });

    it('shows the default value when stage is supported', function() {
      spyOn(Registry.pipeline, 'getStageConfig').and.returnValue(stageConfig);
      var domNode = this.compile('<override-timeout stage="stage"></override-timeout>')(this.scope);
      this.scope.$digest();
      expect(
        domNode
          .find('.default-timeout')
          .text()
          .indexOf('1 hour'),
      ).not.toBe(-1);
      expect(
        domNode
          .find('.default-timeout')
          .text()
          .indexOf('30 minutes'),
      ).not.toBe(-1);
    });

    it('shows the contents when overrideTimeout is set', function() {
      spyOn(Registry.pipeline, 'getStageConfig').and.returnValue(stageConfig);
      this.scope.stage.overrideTimeout = true;
      var domNode = this.compile('<override-timeout stage="stage"></override-timeout>')(this.scope);
      this.scope.$digest();
      expect(domNode.find('input[type="number"]').length).toBe(2);
    });

    it('unsets timeout, removes contents when overrideTimeout is set to false', function() {
      spyOn(Registry.pipeline, 'getStageConfig').and.returnValue(stageConfig);
      this.scope.stage.stageTimeoutMs = 30000;
      this.scope.stage.overrideTimeout = true;
      var domNode = this.compile('<override-timeout stage="stage"></override-timeout>')(this.scope);
      this.scope.$digest();
      expect(domNode.find('input[type="number"]').length).toBe(2);
      this.scope.stage.overrideTimeout = false;
      this.scope.$digest();
      expect(domNode.find('input[type="number"]').length).toBe(0);
      expect(this.scope.stage.stageTimeoutMs).toBeUndefined();
    });
  });

  describe('time conversion', function() {
    it('rounds down', function() {
      this.scope.stage.overrideTimeout = true;
      this.scope.stage.stageTimeoutMs = 30 * 60 * 1000 + 499;
      this.$controller('OverrideTimeoutCtrl', {
        $scope: this.scope,
      });
      this.scope.$digest();
      expect(this.scope.vm.minutes).toBe(30);
    });

    it('rolls minutes over to hours', function() {
      this.scope.stage.overrideTimeout = true;
      this.scope.stage.stageTimeoutMs = 95 * 60 * 1000;
      var ctrl = this.$controller('OverrideTimeoutCtrl', {
        $scope: this.scope,
      });
      this.scope.$digest();
      expect(this.scope.vm.minutes).toBe(35);
      expect(this.scope.vm.hours).toBe(1);

      this.scope.vm.hours = 0;
      this.scope.vm.minutes = 99;
      ctrl.synchronizeTimeout();
      this.scope.$digest();
      expect(this.scope.vm.hours).toBe(1);
      expect(this.scope.vm.minutes).toBe(39);
    });
  });
});
