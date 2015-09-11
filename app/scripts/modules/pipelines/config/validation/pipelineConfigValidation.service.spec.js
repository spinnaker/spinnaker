'use strict';

describe('pipelineConfigValidator', function () {

  beforeEach(
    window.module(
      require('./pipelineConfigValidation.service'),
      require('../pipelineConfig.module.js')
    )
  );

  beforeEach(window.inject(function (pipelineConfigValidator, pipelineConfig) {
    this.validator = pipelineConfigValidator;
    this.pipelineConfig = pipelineConfig;
  }));

  describe('validation', function () {

    it('performs validation against stages and triggers where declared, ignores others', function () {
      spyOn(this.pipelineConfig, 'getTriggerConfig').and.callFake(function (type) {
        if (type === 'withTriggerValidation') {
          return {
            validators: [
              {
                type: 'requiredField',
                fieldName: 'boo',
                message: 'boo is required',
              }
            ]
          };
        }
        return {};
      });
      spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
        if (stage.type === 'withValidation') {
          return {
            validators: [
              {
                type: 'requiredField',
                fieldName: 'bar',
                message: 'bar is required',
              }
            ]
          };
        }
        return {};
      });

      var pipeline = {
        stages: [
          { type: 'withValidation', },
          { type: 'no-validation' },
        ],
        triggers: [
          { type: 'withTriggerValidation' },
          { type: 'withoutValidation' },
        ]
      };

      var validationMessages = this.validator.validatePipeline(pipeline);
      expect(validationMessages.length).toBe(2);
      expect(validationMessages[0]).toBe('boo is required');
      expect(validationMessages[1]).toBe('bar is required');
    });

    it('executes all validators', function () {
      spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
        if (stage.type === 'withValidation') {
          return {
            validators: [
              {
                type: 'requiredField',
                fieldName: 'bar',
                message: 'bar is required',
              },
              {
                type: 'requiredField',
                fieldName: 'foo',
                message: 'bar is also required'
              },
            ]
          };
        } else {
          return {};
        }
      });

      var pipeline = {
        stages: [
          { type: 'withValidation', },
          { type: 'no-validation' }
        ]
      };

      var validationMessages = this.validator.validatePipeline(pipeline);
      expect(validationMessages.length).toBe(2);

      pipeline.stages[0].foo = 'a';
      validationMessages = this.validator.validatePipeline(pipeline);
      expect(validationMessages.length).toBe(1);
      expect(validationMessages[0]).toBe('bar is required');
    });

  });

  describe('validators', function () {

    describe('stageBeforeType', function () {
      it('fails if no stage is first or not preceded by declared stage type', function () {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
          if (stage.type === 'withValidation') {
            return {
              validators: [
                {
                  type: 'stageBeforeType',
                  stageType: 'prereq',
                  message: 'need a prereq',
                },
              ]
            };
          } else {
            return {};
          }
        });

        var pipeline = {
          stages: [
            {type: 'withValidation', refId: 1, requisiteStageRefIds: []},
            {type: 'no-validation', refId: 2, requisiteStageRefIds: []}
          ]
        };

        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages = [
          {type: 'wrongType', refId: 1, requisiteStageRefIds: []},
          {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'prereq';
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'prereq', refId: 1, requisiteStageRefIds: []},
          {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('validates against multiple types if present', function () {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
          if (stage.type === 'withValidation') {
            return {
              validators: [
                {
                  type: 'stageBeforeType',
                  stageTypes: ['one', 'two'],
                  message: 'need a prereq',
                },
              ]
            };
          } else {
            return {};
          }
        });

        var pipeline = {
          stages: [
            {type: 'three', refId: 1, requisiteStageRefIds: []},
            {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
          ]
        };

        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'one';
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'two', refId: 1, requisiteStageRefIds: []},
          {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });
    });

    describe('checkRequiredField', function () {
      beforeEach(function () {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
          if (stage.type === 'simpleField') {
            return {
              validators: [
                {
                  type: 'requiredField',
                  fieldName: 'foo',
                  message: 'need a foo',
                },
              ]
            };
          }
          if (stage.type === 'nestedField') {
            return {
              validators: [
                {
                  type: 'requiredField',
                  fieldName: 'foo.bar.baz',
                  message: 'need a foo.bar.baz',
                },
              ]
            };
          }
          return {};
        });
      });

      it('non-nested field', function () {
        var pipeline = { stages: [
          { type: 'simpleField' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo');

        pipeline.stages[0].foo = 4;
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = 0;
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = '';
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = null;
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
      });

      it('nested field', function () {
        var pipeline = { stages: [
          { type: 'nestedField' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo.bar.baz');

        pipeline.stages[0].foo = 4;
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: 1 };
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: null }};
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: '' }};
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: 0 }};
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = { bar: { baz: 'ok' }};
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('empty array', function () {
        var pipeline = { stages: [
          { type: 'simpleField', foo: [] }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo');

        pipeline.stages[0].foo.push(1);
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });
    });

    describe('targetImpedance', function () {
      beforeEach(function () {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function (stage) {
          if (stage.type === 'targetCheck') {
            return {
              validators: [
                {
                  type: 'targetImpedance',
                  message: 'mismatch detected',
                },
              ]
            };
          }
          return {};
        });
      });

      it('flags when no deploy step present', function () {
        var pipeline = { stages: [
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('passes without stack or details', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes with stack', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes with freeFormDetails', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', freeFormDetails: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck--main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes with stack and freeFormDetails', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main-foo' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes single region', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes multiple regions in same deploy stage', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }},
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1', 'us-west-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('passes multiple regions scattered across deploy stages', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1', 'us-west-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('flags credentials mismatch', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'prod', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags region mismatch', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch - no stack or freeFormDetails', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck2' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch on stack', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'staging', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch on freeFormDetails', function () {
        var pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck--bar' }
        ]};
        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });
    });
  });

});
