'use strict';

describe('Controller: renamePipelineModal', function() {

  beforeEach(module('deckApp.pipelines.rename'));

  beforeEach(inject(function($controller, $rootScope, _, $log, $q, pipelineConfigService) {
    this.$q = $q;
    this.initializeController = function(application, pipeline) {
      this.$scope = $rootScope.$new();
      this.pipelineConfigService = pipelineConfigService;
      this.$modalInstance = { close: angular.noop };
      this.controller = $controller('RenamePipelineModalCtrl', {
        $scope: this.$scope,
        application: application,
        pipeline: pipeline,
        pipelineConfigService: this.pipelineConfigService,
        $modalInstance: this.$modalInstance,
        $log: $log,
        _: _,
      });
    };
  }));

  beforeEach(function() {
    this.pipelines = [
      {name: 'a'},
      {name: 'b'},
      {name: 'c'}
    ];

    this.application = { name: 'the_app', pipelines: [this.pipelines[0], this.pipelines[1], this.pipelines[2]]};
    this.initializeController(this.application, this.pipelines[1]);

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

      this.$scope.newName = 'd';

      spyOn(this.pipelineConfigService, 'renamePipeline').and.callFake(function (applicationName, currentName, newName) {
        submittedNewName = newName;
        submittedCurrentName = currentName;
        submittedApplication = applicationName;
        return $q.when(null);
      });
      spyOn(this.$modalInstance, 'close');

      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(submittedNewName).toBe('d');
      expect(submittedCurrentName).toBe('b');
      expect(submittedApplication).toBe('the_app');
      expect(this.application.pipelines[1].name).toEqual('d');
    });

    it('sets error flag, message when save is rejected', function() {
      var $q = this.$q;
      spyOn(this.pipelineConfigService, 'renamePipeline').and.callFake(function () {
        return $q.reject({message: 'something went wrong'});
      });

      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', function() {
      var $q = this.$q;
      spyOn(this.pipelineConfigService, 'renamePipeline').and.callFake(function () {
        return $q.reject({});
      });


      this.controller.renamePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('No message provided');
    });
  });

});
