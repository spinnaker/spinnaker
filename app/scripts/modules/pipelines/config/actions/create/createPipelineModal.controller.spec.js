'use strict';

describe('Controller: createPipelineModal', function() {

  beforeEach(module('deckApp.pipelines.create'));

  beforeEach(inject(function($controller, $rootScope, _, $log, $q, pipelineConfigService) {
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
        _: _,
        $log: $log
      });
    };
  }));

  describe('template instantiation', function() {

    it('provides a default value when no templates exist',function() {
      this.initializeController({});
      expect(this.$scope.templates.length).toBe(1);
      expect(this.$scope.templates[0].name).toBe('None');
    });

    it('includes the default value when templates exist', function() {
      this.initializeController({pipelines: [ { name: 'some pipeline' } ]});
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
    });

    it('initializes command with the default template', function() {
      this.initializeController({pipelines: [ { name: 'some pipeline' } ]});
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
      expect(this.$scope.command.template.name).toBe('None');
    });

    it('sets all pipeline names on the scope to be used by unique validator', function() {
      this.initializeController({pipelines: [ { name: 'a' }, { name: 'b' } ]});
      expect(this.$scope.templates.length).toBe(3);
      expect(this.$scope.existingNames).toEqual(['None', 'a', 'b']);
    });
  });

  describe('pipeline submission', function() {

    it('saves pipeline, adds it to application, and closes modal', function() {
      var $q = this.$q;
      var submitted = null;
      this.initializeController({name:'the_app'});
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
    });

    it('sets error flag, message when save is rejected', function() {
      var $q = this.$q;
      this.initializeController({name:'the_app'});
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function () {
        return $q.reject({message: 'something went wrong'});
      });

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', function() {
      var $q = this.$q;
      this.initializeController({name:'the_app'});
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
