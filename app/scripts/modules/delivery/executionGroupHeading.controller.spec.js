'use strict';

describe('Controller: ExecutionGroupHeading', function () {

  beforeEach(module('deckApp.delivery'));

  beforeEach(inject(function ($controller, $rootScope, $q) {
    this.$scope = $rootScope.$new();
    this.$controller = $controller;
    this.$q = $q;
  }));

  describe('triggerPipeline', function() {
    beforeEach(function() {
      var $q = this.$q;
      this.$stateParams = {
        application: 'app'
      };

      this.initializeController = function() {
        this.pipelineConfigService = {
          triggerPipeline: function() {
            return $q.when(null);
          }
        };
        this.executionsService = {
          waitUntilNewTriggeredPipelineAppears: angular.noop,
          forceRefresh: angular.noop
        };

        this.controller = this.$controller('executionGroupHeading', {
          $scope: this.$scope,
          pipelineConfigService: this.pipelineConfigService,
          executionsService: this.executionsService,
          $stateParams: this.$stateParams
        });
      };
    });

    it('sets flag, waits for new execution to appear, ignoring any currently enqueued or running pipelines', function() {
      this.$scope.executions = [
        { status: 'RUNNING', id: 'exec-1' },
        { status: 'NOT_STARTED', id: 'exec-2' },
        { status: 'COMPLETED', id: 'exec-3' },
      ];

      this.$scope.value = 'pipeline name a';

      this.initializeController();

      spyOn(this.executionsService, 'waitUntilNewTriggeredPipelineAppears').and.returnValue(this.$q.when(null));
      spyOn(this.executionsService, 'forceRefresh');

      this.controller.triggerPipeline();
      expect(this.$scope.viewState.triggeringExecution).toBe(true);

      this.$scope.$digest();

      expect(this.executionsService.waitUntilNewTriggeredPipelineAppears).toHaveBeenCalledWith('pipeline name a', ['exec-1', 'exec-2']);
      expect(this.executionsService.forceRefresh).toHaveBeenCalled();
      expect(this.$scope.viewState.triggeringExecution).toBe(false);

    });
  });

});
