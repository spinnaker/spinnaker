'use strict';

describe('Controller: renamePipelineModal', function() {
  const angular = require('angular');


  beforeEach(
    window.module(
      require('./editPipelineJson.module.js')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, _) {
    this.initializeController = function (pipeline) {
      this.$scope = $rootScope.$new();
      this.$modalInstance = { close: angular.noop };
      this.controller = $controller('EditPipelineJsonModalCtrl', {
        $scope: this.$scope,
        pipeline: pipeline,
        $modalInstance: this.$modalInstance,
        _: _,
      });
    };
  }));

  it ('controller removes name, application, appConfig, all Restangular fields and hash keys', function() {

    var pipeline = {
      name: 'foo',
      application: 'myApp',
      appConfig: 'appConfig',
      stage: {
        foo: [
          {}
        ],
        bar: {},
        baz: '',
        bat: 4
      }
    };

    this.initializeController(pipeline);

    // sprinkle some hash keys into the pipeline
    pipeline.stage.$$hashKey = '01D';
    pipeline.stage.foo[0].$$hashKey = '01F';
    pipeline.stage.bar.$$hashKey = '01G';
    pipeline.plain = function() { return pipeline; };
    spyOn(pipeline, 'plain').and.callThrough();

    this.controller.initialize();

    var converted = JSON.parse(this.$scope.command.pipelineJSON);

    // restangular fields
    expect(pipeline.plain).toHaveBeenCalled();

    // name
    expect(converted.name).toBeUndefined();
    expect(converted.application).toBeUndefined();
    expect(converted.appConfig).toBe('appConfig');

    // hash keys
    expect(converted.stage.$$hashKey).toBeUndefined();
    expect(converted.stage.foo[0].$$hashKey).toBeUndefined();
    expect(converted.stage.bar.$$hashKey).toBeUndefined();
  });

  it ('updatePipeline updates fields, removing name if added', function() {
    var pipeline = {
      application: 'myApp',
      name: 'foo',
      stage: {
        foo: [
          {}
        ],
        bar: {},
        baz: '',
        bat: 4
      }
    };

    this.initializeController(pipeline);
    spyOn(this.$modalInstance, 'close');

    var converted = JSON.parse(this.$scope.command.pipelineJSON);
    converted.application = 'someOtherApp';
    converted.name = 'replacedName';
    converted.bar = { updated: true };
    this.$scope.command.pipelineJSON = JSON.stringify(converted);

    this.controller.updatePipeline();

    expect(pipeline.application).toBe('myApp');
    expect(pipeline.bar.updated).toBe(true);

    expect(this.$modalInstance.close).toHaveBeenCalled();
  });

  it ('updateApplicationFromJson displays an error message when malformed JSON provided', function() {
    var pipeline = {};

    this.initializeController(pipeline);

    this.$scope.command = {pipelineJSON: 'This is not very good JSON'};
    this.controller.updatePipeline();

    expect(this.$scope.command.invalid).toBe(true);
    expect(this.$scope.command.errorMessage).not.toBe(null);
  });

  it ('updateApplicationFromJson sets the stage counter based on max value of refIds', function () {
    var pipeline = {
      application: 'myApp',
      name: 'foo',
      stageCounter: 4,
      parallel: true,
      stages: [
        {refId: '3'},
        {refId: '13'}
      ]
    };
    this.initializeController(pipeline);
    this.$scope.command = {pipelineJSON: JSON.stringify(pipeline)};
    this.controller.updatePipeline();

    expect(pipeline.stageCounter).toBe(13);
  });

});
