import { APPLICATION_MODEL_BUILDER } from 'core/application/applicationModel.builder';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

describe('Controller: renamePipelineModal', function() {
  const angular = require('angular');

  beforeEach(window.module(require('./rename.module').name, APPLICATION_MODEL_BUILDER));

  beforeEach(
    window.inject(function($controller, $rootScope, $log, $q, applicationModelBuilder) {
      this.$q = $q;
      this.$rootScope = $rootScope;
      this.application = applicationModelBuilder.createApplicationForTests('app', {
        key: 'pipelineConfigs',
        lazy: true,
        loader: () => this.$q.when(this.application.pipelineConfigs.data),
        onLoad: (_app, data) => this.$q.when(data),
      });
      this.initializeController = function(pipeline) {
        this.$scope = $rootScope.$new();
        this.$uibModalInstance = { close: angular.noop };
        this.controller = $controller('RenamePipelineModalCtrl', {
          $scope: this.$scope,
          application: this.application,
          pipeline: pipeline,
          $uibModalInstance: this.$uibModalInstance,
          $log: $log,
        });
        this.$scope.$digest();
      };
    }),
  );

  beforeEach(function() {
    this.pipelines = [{ name: 'a' }, { name: 'b' }, { name: 'c' }];

    this.application.pipelineConfigs.activate();
    this.$rootScope.$digest();
    this.application.pipelineConfigs.data = [this.pipelines[0], this.pipelines[1], this.pipelines[2]];
    this.initializeController(this.pipelines[1]);
  });

  describe('controller initialization', function() {
    it('sets all pipeline names (except this one) on the scope to be used by unique validator', function() {
      expect(this.$scope.existingNames).toEqual(['a', 'c']);
    });
  });

  describe('pipeline renaming', function() {
    it('deletes pipeline, removes it from application, and closes modal', function() {
      var $q = this.$q;
      var submittedNewName = null,
        submittedCurrentName = null,
        submittedApplication = null;

      this.$scope.command = {
        newName: 'd',
      };

      spyOn(PipelineConfigService, 'renamePipeline').and.callFake(function(applicationName, _, currentName, newName) {
        submittedNewName = newName;
        submittedCurrentName = currentName;
        submittedApplication = applicationName;
        return $q.when(null);
      });
      spyOn(this.$uibModalInstance, 'close');

      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(submittedNewName).toBe('d');
      expect(submittedCurrentName).toBe('b');
      expect(submittedApplication).toBe('app');
      expect(this.application.pipelineConfigs.data[1].name).toEqual('d');
    });

    it('sets error flag, message when save is rejected', function() {
      var $q = this.$q;
      spyOn(PipelineConfigService, 'renamePipeline').and.callFake(function() {
        return $q.reject({ message: 'something went wrong' });
      });

      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', function() {
      var $q = this.$q;
      spyOn(PipelineConfigService, 'renamePipeline').and.callFake(function() {
        return $q.reject({});
      });

      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('No message provided');
    });
  });
});
