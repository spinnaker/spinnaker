'use strict';

describe('Controller: deletePipelineModal', function() {

  beforeEach(module('deckApp.pipelines.delete'));

  beforeEach(inject(function($controller, $rootScope, $log, $q, pipelineConfigService) {
    this.$q = $q;
    this.initializeController = function(application, pipeline) {
      this.$scope = $rootScope.$new();
      this.pipelineConfigService = pipelineConfigService;
      this.$modalInstance = { close: angular.noop };
      this.controller = $controller('DeletePipelineModalCtrl', {
        $scope: this.$scope,
        application: application,
        pipeline: pipeline,
        pipelineConfigService: this.pipelineConfigService,
        $modalInstance: this.$modalInstance,
        $log: $log
      });
    };
  }));

  describe('pipeline deletion', function() {

    beforeEach(function() {
      this.pipelines = [
        {name: 'a'},
        {name: 'b'},
        {name: 'c'}
      ];

      this.application = { name: 'the_app', pipelines: [this.pipelines[0], this.pipelines[1], this.pipelines[2]]};
      this.initializeController(this.application, this.pipelines[1]);

    });

    it('deletes pipeline, removes it from application, and closes modal', function() {
      var $q = this.$q;
      var submittedPipeline = null,
          submittedApplication = null;

      spyOn(this.pipelineConfigService, 'deletePipeline').and.callFake(function (applicationName, pipelineName) {
        submittedPipeline = pipelineName;
        submittedApplication = applicationName;
        return $q.when(null);
      });
      spyOn(this.$modalInstance, 'close');

      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(submittedPipeline).toBe('b');
      expect(submittedApplication).toBe('the_app');
      expect(this.application.pipelines).toEqual([this.pipelines[0], this.pipelines[2]]);
    });

    it('sets error flag, message when save is rejected', function() {
      var $q = this.$q;
      spyOn(this.pipelineConfigService, 'deletePipeline').and.callFake(function () {
        return $q.reject({message: 'something went wrong'});
      });

      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', function() {
      var $q = this.$q;
      spyOn(this.pipelineConfigService, 'deletePipeline').and.callFake(function () {
        return $q.reject({});
      });


      this.controller.deletePipeline();
      this.$scope.$digest();

      expect(this.$scope.viewState.saveError).toBe(true);
      expect(this.$scope.viewState.errorMessage).toBe('No message provided');
    });
  });

});
