'use strict';

describe('Controller: createPipelineModal', function() {
  const angular = require('angular');

  beforeEach(
    window.module(
      require('./createPipelineModal.controller')
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, _, $log, $q, pipelineConfigService) {
    this.$q = $q;
    this.initializeController = function(application) {
      this.$scope = $rootScope.$new();
      this.pipelineConfigService = pipelineConfigService;
      this.$modalInstance = { close: angular.noop };
      this.controller = $controller('CreatePipelineModalCtrl', {
        $scope: this.$scope,
        application: application,
        pipelineConfigService: this.pipelineConfigService,
        $modalInstance: this.$modalInstance,
        target: null,
        _: _,
        $log: $log,
      });
    };
  }));

  describe('template instantiation', function() {

    it('provides a default value when no templates exist', function() {
      this.initializeController({name: 'the-app'});
      var template = this.$scope.templates[0];
      expect(this.$scope.templates.length).toBe(1);
      expect(template.name).toBe('None');
      expect(template.application).toBe('the-app');
      expect(template.triggers).toEqual([]);
      expect(template.stages).toEqual([]);
    });

    it('includes the default value when templates exist', function() {
      this.initializeController({pipelineConfigs: [ { name: 'some pipeline' } ]});
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
    });

    it('initializes command with the default template', function() {
      this.initializeController({pipelineConfigs: [ { name: 'some pipeline' } ]});
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
      expect(this.$scope.command.template.name).toBe('None');
    });

    it('sets all pipeline names on the scope to be used by unique validator', function() {
      this.initializeController({pipelineConfigs: [ { name: 'a' }, { name: 'b' } ]});
      expect(this.$scope.templates.length).toBe(3);
      expect(this.$scope.existingNames).toEqual(['None', 'a', 'b']);
    });
  });

  describe('pipeline submission', function() {

    it('saves pipeline, adds it to application, and closes modal', function () {
      var $q = this.$q;
      var submitted = null;
      var application = {
        name: 'the_app'
      };
      application.reloadPipelineConfigs = function () {
        application.pipelineConfigs = [
          {name: 'new pipeline', id: '1234-5678'}
        ];
        return $q.when(null);
      };
      this.initializeController(application);
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$modalInstance, 'close');

      this.$scope.command.name = 'new pipeline';

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('the_app');
      expect(submitted.stages).toEqual([]);
      expect(submitted.triggers).toEqual([]);
      expect(this.$modalInstance.close).toHaveBeenCalledWith('1234-5678');
    });

    it('uses copy of plain version of pipeline', function () {
      // Instead of introducing Restangular as a dependency, mock out the `fromServer` and `plain` fields
      // and verify `plain` is called
      var $q = this.$q;
      var submitted = null;
      var toCopy = {
        application: 'the_app',
        name: 'old_name',
        stages: [{name: 'the_stage'}],
        triggers: [{name: 'the_trigger'}],
        fromServer: true,
        plain: angular.noop
      };
      var application = {
        name: 'the_app',
        pipelineConfigs: [toCopy],
      };
      application.reloadPipelineConfigs = function () {
        application.pipelineConfigs = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      };

      spyOn(toCopy, 'plain').and.callFake(function () {
        toCopy.isPlainNow = true;
        return toCopy;
      });
      this.initializeController(application);
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$modalInstance, 'close');

      this.$scope.command.name = 'new pipeline';
      this.$scope.command.template = toCopy;

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('the_app');
      expect(submitted.stages.length).toBe(1);
      expect(submitted.triggers.length).toBe(1);
      expect(submitted.isPlainNow).toBe(true);
    });

    it('should insert new pipeline as last one in application and set its index', function () {
      var $q = this.$q;
      var submitted = null;
      var application = {
        name: 'the_app',
        pipelineConfigs: [{name: 'x'}],
      };
      application.reloadPipelineConfigs = function () {
        application.pipelineConfigs = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      };

      this.initializeController(application);
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$modalInstance, 'close');

      this.$scope.command.name = 'new pipeline';

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.index).toBe(1);
    });

    it('sets error flag, message when save is rejected', function () {
      var $q = this.$q;
      this.initializeController({name: 'the_app'});
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function () {
        return $q.reject({data: {message: 'something went wrong'}});
      });

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', function () {
      var $q = this.$q;
      this.initializeController({name: 'the_app'});
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function () {
        return $q.reject({});
      });


      this.controller.createPipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('No message provided');
    });
  });
});
