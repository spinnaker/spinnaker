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

  describe('getPipelines', function () {
    it('should return pipelines sorted by index', function() {
      var result = null;
      var fromServer = [
        { name: 'second', index: 1, stages: []},
        { name: 'last', index: 3, stages: []},
        { name: 'first', index: 0, stages: []},
        { name: 'third', index: 2, stages: []},
      ];
      this.$http.expectGET('/applications/app/pipelineConfigs').respond(200, fromServer);

      this.service.getPipelinesForApplication('app').then(function(pipelines) {
        result = pipelines;
      });
      this.$scope.$digest();
      this.$http.flush();

      expect(_.pluck(result, 'name')).toEqual(['first', 'second', 'third', 'last']);
    });

    it('should fix sort order of pipelines on initialization: 0..n, index collisions sorted alphabetically', function() {
      var fromServer = [
        { name: 'second', index: 1, stages: []},
        { name: 'last', index: 5, stages: []},
        { name: 'first', index: -3, stages: []},
        { name: 'duplicateIndex', index: 5, stages: []},
      ];

      var posted = [];
      this.$http.expectGET('/applications/app/pipelineConfigs').respond(200, fromServer);
      this.$http.whenPOST('/pipelines', function(data) {
        var json = JSON.parse(data);
        posted.push({index: json.index, name: json.name});
        return true;
      }).respond(200, '');

      this.service.getPipelinesForApplication('app');
      this.$scope.$digest();
      this.$http.flush();

      expect(posted).toEqual([
        { name: 'first', index: 0 },
        { name: 'duplicateIndex', index: 2 },
        { name: 'last', index: 3 },
      ]);
    });
  });
});

