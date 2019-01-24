import { APPLICATION_MODEL_BUILDER } from 'core/application/applicationModel.builder';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

describe('Controller: deletePipelineModal', function() {
  const angular = require('angular');

  beforeEach(window.module(require('./delete.module').name, APPLICATION_MODEL_BUILDER));
  beforeEach(
    window.inject(function($controller, $rootScope, $log, $q, $state, applicationModelBuilder) {
      this.$rootScope = $rootScope;
      this.$q = $q;
      this.application = applicationModelBuilder.createApplicationForTests('app', {
        key: 'pipelineConfigs',
        lazy: true,
        loader: () => this.$q.when(this.application.pipelineConfigs.data),
        onLoad: (_app, data) => this.$q.when(data),
      });
      this.initializeController = function(pipeline) {
        this.$state = $state;
        this.$scope = $rootScope.$new();
        this.$uibModalInstance = { close: angular.noop };
        this.controller = $controller('DeletePipelineModalCtrl', {
          $scope: this.$scope,
          application: this.application,
          pipeline: pipeline,
          $uibModalInstance: this.$uibModalInstance,
          $log: $log,
          $state: $state,
        });
        this.$scope.$digest();
      };
    }),
  );

  describe('pipeline deletion', function() {
    beforeEach(function() {
      this.pipelines = [
        { name: 'a', index: 0, id: 'A' },
        { name: 'b', index: 1, id: 'B' },
        { name: 'c', index: 2, id: 'C' },
      ];

      this.application.pipelineConfigs.activate();
      this.$rootScope.$digest();

      this.application.pipelineConfigs.data = [this.pipelines[0], this.pipelines[1], this.pipelines[2]];
      this.initializeController(this.pipelines[1]);
    });

    it('deletes pipeline, removes it from application, reindexes latter pipelines, and closes modal', function() {
      var $q = this.$q;
      var submittedPipeline = null,
        submittedApplication = null,
        newStateTarget = null,
        newStateOptions = null;

      spyOn(PipelineConfigService, 'deletePipeline').and.callFake(function(applicationName, {}, pipelineName) {
        submittedPipeline = pipelineName;
        submittedApplication = applicationName;
        return $q.when(null);
      });
      spyOn(PipelineConfigService, 'savePipeline');
      spyOn(this.$uibModalInstance, 'close');
      spyOn(this.$state, 'go').and.callFake(function(target, params, options) {
        newStateTarget = target;
        newStateOptions = options;
      });

      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(submittedPipeline).toBe('b');
      expect(submittedApplication).toBe('app');
      expect(this.application.pipelineConfigs.data).toEqual([this.pipelines[0], this.pipelines[2]]);
      expect(PipelineConfigService.savePipeline).toHaveBeenCalledWith(this.pipelines[2]);
      expect(PipelineConfigService.savePipeline.calls.count()).toEqual(1);
      expect(this.pipelines[2].index).toBe(1);
      expect(newStateTarget).toBe('^.executions');
      expect(newStateOptions).toEqual({ location: 'replace' });
    });

    it('sets error flag, message when delete is rejected', function() {
      var $q = this.$q;
      spyOn(PipelineConfigService, 'deletePipeline').and.callFake(function() {
        return $q.reject({ message: 'something went wrong' });
      });

      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.deleteError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed delete', function() {
      var $q = this.$q;
      spyOn(PipelineConfigService, 'deletePipeline').and.callFake(function() {
        return $q.reject({});
      });

      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.deleteError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('No message provided');
    });
  });
});
