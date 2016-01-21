'use strict';

describe('pipelineConfigService', function () {
  beforeEach(
    window.module(
      require('./pipelineConfigService'),
      require('../../../utils/lodash.js')
    )
  );

  beforeEach(window.inject(function (pipelineConfigService, $httpBackend, $rootScope, ___) {
    this._ = ___;
    this.service = pipelineConfigService;
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

  describe('getAvailableUpstreamStages', function() {

    beforeEach(function() {
      this.a = { refId: 1, requisiteStageRefIds: [] };
      this.b = { refId: 2, requisiteStageRefIds: [] };
      this.c = { refId: 3, requisiteStageRefIds: [] };
      this.d = { refId: 4, requisiteStageRefIds: [] };

      this.pipeline = { stages: [ this.a, this.b, this.c, this.d ]};

      this.connect = function(child, parent) {
        this[child].requisiteStageRefIds.push(this[parent].refId);
      };

      this.expectCandidates = function(test, expected) {
        var target = [];
        expected.forEach(function(stage) {
          target.push(this[stage]);
        }.bind(this));
        expect(this.service.getDependencyCandidateStages(this.pipeline, this[test])).toEqual(target);
      };
    });

    it('filters out provided stage', function() {
      this.expectCandidates('a', ['b', 'c', 'd']);
      this.expectCandidates('b', ['a', 'c', 'd']);
      this.expectCandidates('c', ['a', 'b', 'd']);
      this.expectCandidates('d', ['a', 'b', 'c']);
    });

    it('filters out direct dependent', function() {
      this.connect('b', 'a');
      this.expectCandidates('a', ['c', 'd']);
    });

    it('filters out multiple direct dependents', function() {
      this.connect('b', 'a');
      this.connect('c', 'a');
      this.expectCandidates('a', ['d']);
    });

    it('filters out existing upstream stages and indirect dependents', function() {
      this.connect('b', 'a');
      this.connect('c', 'b');
      this.expectCandidates('a', ['d']);
      this.expectCandidates('b', ['d']);
      this.expectCandidates('c', ['a', 'd']);
      this.expectCandidates('d', ['a', 'b', 'c']);
    });

    it('can depend on descendant stages of siblings', function() {
      this.connect('b', 'a');
      this.connect('c', 'b');
      this.connect('d', 'a');
      this.expectCandidates('a', []);
      this.expectCandidates('d', ['b', 'c']);
    });
  });

  describe('getAllUpstreamDependencies', function() {
    beforeEach(function() {
      this.a = { refId: 1, requisiteStageRefIds: [] };
      this.b = { refId: 2, requisiteStageRefIds: [] };
      this.c = { refId: 3, requisiteStageRefIds: [] };
      this.d = { refId: 4, requisiteStageRefIds: [] };

      this.pipeline = { stages: [ this.a, this.b, this.c, this.d ]};

      this.connect = function (child, parent) {
        this[child].requisiteStageRefIds.push(this[parent].refId);
      };

      this.expectDependencies = function(test, expected) {
        var target = [];
        expected.forEach(function(stage) {
          target.push(this[stage]);
        }.bind(this));
        expect(this.service.getAllUpstreamDependencies(this.pipeline, this[test])).toEqual(target);
      };
    });

    it('returns an empty list when no dependencies exist', function() {
      this.expectDependencies('a', []);
      this.expectDependencies('b', []);
      this.expectDependencies('c', []);
      this.expectDependencies('d', []);
    });

    it('returns a single direct dependency', function() {
      this.connect('b', 'a');
      this.connect('c', 'a');

      this.expectDependencies('a', []);
      this.expectDependencies('b', ['a']);
      this.expectDependencies('c', ['a']);
      this.expectDependencies('d', []);
    });

    it('returns multiple direct dependencies', function() {
      this.connect('c', 'a');
      this.connect('c', 'b');

      this.expectDependencies('a', []);
      this.expectDependencies('b', []);
      this.expectDependencies('c', ['a', 'b']);
      this.expectDependencies('d', []);
    });

    it('returns ancestor upstream dependencies', function() {
      this.connect('b', 'a');
      this.connect('c', 'b');
      this.connect('d', 'c');

      this.expectDependencies('a', []);
      this.expectDependencies('b', ['a']);
      this.expectDependencies('c', ['b', 'a']);
      this.expectDependencies('d', ['c', 'b', 'a']);
    });

    it('returns ancestors: multiple direct dependencies', function() {
      this.connect('b', 'a');
      this.connect('c', 'a');
      this.connect('d', 'b');
      this.connect('d', 'c');

      this.expectDependencies('a', []);
      this.expectDependencies('b', ['a']);
      this.expectDependencies('c', ['a']);
      this.expectDependencies('d', ['b', 'a', 'c']);
    });

    it('returns ancestors: multiple ancestor dependencies', function() {
      this.connect('c', 'a');
      this.connect('c', 'b');
      this.connect('d', 'c');

      this.expectDependencies('a', []);
      this.expectDependencies('b', []);
      this.expectDependencies('c', ['a', 'b']);
      this.expectDependencies('d', ['c', 'a', 'b']);

    });
  });
});

