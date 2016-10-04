import modelBuilderModule from '../../application/applicationModel.builder';

describe('Controller: createPipelineModal', function() {
  const angular = require('angular');
  var application;

  beforeEach(
    window.module(
      require('./createPipelineModal.controller'),
      modelBuilderModule
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, $log, $q, pipelineConfigService, applicationModelBuilder) {
    this.$q = $q;
    this.initializeController = function(configs = []) {
      application = applicationModelBuilder.createApplication(
        {
          key: 'pipelineConfigs',
          lazy: true,
          loader: () => this.$q.when(null),
          onLoad: () => this.$q.when(null),
        },
        {
          key: 'strategyConfigs',
          lazy: true,
          loader: () => this.$q.when(null),
          onLoad: () => this.$q.when(null),
        }
      );
      application.pipelineConfigs.data = configs;
      this.$scope = $rootScope.$new();
      this.pipelineConfigService = pipelineConfigService;
      this.$uibModalInstance = { close: angular.noop };
      this.controller = $controller('CreatePipelineModalCtrl', {
        $scope: this.$scope,
        application: application,
        pipelineConfigService: this.pipelineConfigService,
        $uibModalInstance: this.$uibModalInstance,
        target: null,
        _: _,
        $log: $log,
      });
    };
  }));

  describe('template instantiation', function() {

    it('provides a default value when no templates exist', function() {
      this.initializeController();
      var template = this.$scope.templates[0];
      expect(this.$scope.templates.length).toBe(1);
      expect(template.name).toBe('None');
      expect(template.application).toBe('app');
      expect(template.triggers).toEqual([]);
      expect(template.stages).toEqual([]);
    });

    it('includes the default value when templates exist', function() {
      this.initializeController([ { name: 'some pipeline' } ]);
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
    });

    it('initializes command with the default template', function() {
      this.initializeController([ { name: 'some pipeline' } ]);
      expect(this.$scope.templates.length).toBe(2);
      expect(this.$scope.templates[0].name).toBe('None');
      expect(this.$scope.templates[1].name).toBe('some pipeline');
      expect(this.$scope.command.template.name).toBe('None');
    });

    it('sets all pipeline names on the scope to be used by unique validator', function() {
      this.initializeController([ { name: 'a' }, { name: 'b' } ]);
      expect(this.$scope.templates.length).toBe(3);
      expect(this.$scope.existingNames).toEqual(['None', 'a', 'b']);
    });
  });

  describe('pipeline submission', function() {

    it('saves pipeline, adds it to application, and closes modal', function () {
      var $q = this.$q;
      var submitted = null;
      this.initializeController();
      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [
          {name: 'new pipeline', id: '1234-5678'}
        ];
        return $q.when(null);
      });
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$uibModalInstance, 'close');

      this.$scope.command.name = 'new pipeline';

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('app');
      expect(submitted.stages).toEqual([]);
      expect(submitted.triggers).toEqual([]);
      expect(this.$uibModalInstance.close).toHaveBeenCalledWith('1234-5678');
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
        triggers: [{name: 'the_trigger'}]
      };
      this.initializeController([toCopy]);
      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      });
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$uibModalInstance, 'close');

      this.$scope.command.name = 'new pipeline';
      this.$scope.command.template = toCopy;

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('the_app');
      expect(submitted.stages.length).toBe(1);
      expect(submitted.triggers.length).toBe(1);
    });

    it('should insert new pipeline as last one in application and set its index', function () {
      var $q = this.$q;
      var submitted = null;
      this.initializeController([{name: 'x'}]);
      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      });
      spyOn(this.pipelineConfigService, 'savePipeline').and.callFake(function (pipeline) {
        submitted = pipeline;
        return $q.when(null);
      });
      spyOn(this.$uibModalInstance, 'close');

      this.$scope.command.name = 'new pipeline';

      this.controller.createPipeline();
      this.$scope.$digest();

      expect(submitted.index).toBe(1);
    });

    it('sets error flag, message when save is rejected', function () {
      var $q = this.$q;
      this.initializeController();
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
      this.initializeController();
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
