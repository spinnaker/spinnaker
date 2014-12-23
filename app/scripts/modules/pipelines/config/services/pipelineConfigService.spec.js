'use strict';

describe('pipelineConfigService', function () {
  beforeEach(module('deckApp.pipelines'));

  beforeEach(inject(function (pipelineConfigService, settings, $httpBackend, $rootScope) {
    this.service = pipelineConfigService;
    this.settings = settings;
    this.$http = $httpBackend;
    this.$scope = $rootScope.$new();
  }));

  describe('savePipeline', function () {
    it('clears isNew flags, stage name if not present', function () {
      var pipeline = {
        stages: [
          { name: 'explicit name', type: 'bake', isNew: true},
          { name: null, type: 'bake', isNew: true},
          { name: '', type: 'bake', isNew: true}
        ]
      };

      this.$http.expectPOST('/pipelines').respond(200, '');

      this.service.savePipeline(pipeline);
      this.$scope.$digest();

      expect(pipeline.stages[0].name).toBe('explicit name');
      expect(pipeline.stages[1].name).toBeUndefined();
      expect(pipeline.stages[2].name).toBeUndefined();

      expect(pipeline.stages[0].isNew).toBeUndefined();
      expect(pipeline.stages[1].isNew).toBeUndefined();
      expect(pipeline.stages[2].isNew).toBeUndefined();
    });

  });
});

