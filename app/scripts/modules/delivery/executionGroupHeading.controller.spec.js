'use strict';

describe('Controller: ExecutionGroupHeading', function () {

  beforeEach(module('deckApp.delivery.executionGroupHeading.controller'));

  beforeEach(inject(function ($controller, $rootScope, $q) {
    this.$scope = $rootScope.$new();
    this.$controller = $controller;
    this.$q = $q;
    this.$modal = { open: angular.noop };
  }));

  describe('triggerPipeline', function() {
    beforeEach(function() {
      var $q = this.$q;
      this.$scope.application = {};
      this.$scope.filter = {
        execution: { groupBy: 'name' }
      };

      this.initializeController = function(pipelineId) {
        this.pipelineConfigService = {
          triggerPipeline: function() {
            return $q.when({
              ref: '/pipelines/' + pipelineId
            });
          }
        };
        this.executionsService = {
          waitUntilNewTriggeredPipelineAppears: angular.noop,
          forceRefresh: angular.noop,
          getSectionCacheKey: function() { return 'key'; },
        };

        this.controller = this.$controller('executionGroupHeading', {
          $scope: this.$scope,
          pipelineConfigService: this.pipelineConfigService,
          executionsService: this.executionsService,
          application: this.$scope.application,
          $modal: this.$modal,
        });
      };
    });

    it('sets flag, waits for new execution to appear, ignoring any currently enqueued or running pipelines', function() {
      var $scope = this.$scope,
          name = 'pipeline name a';

      $scope.value = name;
      this.initializeController('exec-1');

      spyOn(this.$modal, 'open').and.returnValue({
        result: {
          then: function(arg) {
            arg();
          }
        }
      });

      spyOn(this.executionsService, 'waitUntilNewTriggeredPipelineAppears').and.returnValue(this.$q.when(null));

      this.controller.triggerPipeline();
      expect($scope.viewState.triggeringExecution).toBe(true);

      $scope.$digest();
      expect(this.executionsService.waitUntilNewTriggeredPipelineAppears).toHaveBeenCalledWith($scope.application, 'pipeline name a', 'exec-1');
      expect($scope.viewState.triggeringExecution).toBe(false);

    });
  });

});
