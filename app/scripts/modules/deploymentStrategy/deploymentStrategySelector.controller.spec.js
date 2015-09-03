'use strict';

describe('Controller: deploymentStrategySelector', function() {


  beforeEach(
    window.module(
      require('./deploymentStrategySelector.controller.js'),
      require('../utils/lodash.js')
    )
  );


  beforeEach(window.inject(function($controller, $rootScope, deploymentStrategyService, ___) {
    this._ = ___;

    var strategies = [
      {
        key: ''
      },
      {
        key: 'no-extra-fields'
      },
      {
        key: 'extra-fields-1',
        additionalFields: ['fieldA'],
        additionalFieldsTemplateUrl: 'aaa'
      },
      {
        key: 'extra-fields-2',
        additionalFields: ['fieldA'],
        additionalFieldsTemplateUrl: 'bbb'
      },
    ];

    this.strategies = strategies;

    this.initializeController = function(command) {
      this.$scope = $rootScope.$new();
      this.$scope.command = command;
      this.deploymentStrategyService = deploymentStrategyService;
      this.controller = $controller('DeploymentStrategySelectorCtrl', {
        $scope: this.$scope,
        deploymentStrategyService: this.deploymentStrategyService,
      });
      spyOn(this.deploymentStrategyService, 'listAvailableStrategies').and.returnValue(this.strategies);
      spyOn(this.deploymentStrategyService, 'getStrategy').and.callFake(function(strategy) {
        return _.find(strategies, {key: strategy});
      });
    };
  }));

  describe('changing strategies', function() {

    it('removes previous fields when switching strategies if new strategy does not also have the field', function () {
      var command = { strategy: 'extra-fields-1', fieldA: true };
      this.initializeController(command);
      this.$scope.$digest();
      expect(this.$scope.command.fieldA).not.toBeUndefined();

      // change to strategy that also has the field
      command.strategy = 'extra-fields-2';
      this.$scope.$digest();
      expect(this.$scope.command.fieldA).not.toBeUndefined();

      // change to strategy that does not have the field
      command.strategy = 'no-extra-fields';
      this.$scope.$digest();
      expect(this.$scope.command.fieldA).toBeUndefined();
    });

    it('removes template when not present', function() {
      var command = { strategy: '' };
      this.initializeController(command);
      this.$scope.$digest();
      expect(this.$scope.additionalFieldsTemplateUrl).toBe(null);

      // change to strategy that has a template
      command.strategy = 'extra-fields-2';
      this.$scope.$digest();
      expect(this.$scope.additionalFieldsTemplateUrl).toBe('bbb');

      // change to strategy that does not have a template
      command.strategy = 'no-extra-fields';
      this.$scope.$digest();
      expect(this.$scope.additionalFieldsTemplateUrl).toBeNull();
    });

  });

});
