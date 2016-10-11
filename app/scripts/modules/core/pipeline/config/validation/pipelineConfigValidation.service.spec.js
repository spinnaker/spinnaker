'use strict';

describe('pipelineConfigValidator', () => {

  var pipeline, validate, messages, pipelineConfigValidator, pipelineConfig, pipelineConfigService, $q;

  beforeEach(
    window.module(
      require('./pipelineConfigValidation.service'),
      require('../pipelineConfig.module.js')
    )
  );

  beforeEach(window.inject((_pipelineConfigValidator_, _pipelineConfig_, _pipelineConfigService_, _$q_, $rootScope) => {
    pipelineConfigValidator = _pipelineConfigValidator_;
    pipelineConfig = _pipelineConfig_;
    pipelineConfigService = _pipelineConfigService_;
    $q = _$q_;
    validate = () => {
      messages = null;
      pipelineConfigValidator.validatePipeline(pipeline).then(result => messages = result);
      $rootScope.$new().$digest();
    };
  }));

  describe('validation', () => {

    it('performs validation against stages and triggers where declared, ignores others', () => {
      spyOn(pipelineConfig, 'getTriggerConfig').and.callFake((type) => {
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
      spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

      pipeline = {
        stages: [
          { type: 'withValidation', },
          { type: 'no-validation' },
        ],
        triggers: [
          { type: 'withTriggerValidation' },
          { type: 'withoutValidation' },
        ]
      };

      validate();
      expect(messages.length).toBe(2);
      expect(messages[0]).toBe('boo is required');
      expect(messages[1]).toBe('bar is required');
    });

    it('executes all validators', () => {
      spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

      pipeline = {
        stages: [
          { type: 'withValidation', },
          { type: 'no-validation' }
        ]
      };

      validate();
      expect(messages.length).toBe(2);

      pipeline.stages[0].foo = 'a';
      validate();
      expect(messages.length).toBe(1);
      expect(messages[0]).toBe('bar is required');
    });

  });

  describe('validators', () => {

    describe('stageOrTriggerBeforeType', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
          if (stage.type === 'withValidationIncludingParent') {
            return {
              validators: [
                {
                  type: 'stageOrTriggerBeforeType',
                  checkParentTriggers: true,
                  stageType: 'prereq',
                  message: 'need a prereq',
                },
              ]
            };
          }
          if (stage.type === 'withValidation') {
            return {
              validators: [
                {
                  type: 'stageOrTriggerBeforeType',
                  stageType: 'prereq',
                  message: 'need a prereq',
                },
              ]
            };
          }
          return {};
        });

        pipeline = {
          stages: [
            {type: 'withValidation', refId: 1, requisiteStageRefIds: []},
            {type: 'no-validation', refId: 2, requisiteStageRefIds: []}
          ],
          triggers: []
        };
      });

      it('fails if no stage/trigger is first or not preceded by declared stage type', () => {
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages = [
          {type: 'wrongType', refId: 1, requisiteStageRefIds: []},
          {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];

        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');
      });

      it('succeeds if preceding stage type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'prereq', refId: 1, requisiteStageRefIds: []},
          {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        validate();
        expect(messages.length).toBe(0);
      });

      it('succeeds if trigger type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'withValidation', refId: 1, requisiteStageRefIds: []}
        ];
        pipeline.triggers = [
          {type: 'prereq'}
        ];

        validate();
        expect(messages.length).toBe(0);
      });

      it('fails if no preceding stage type matches and no trigger type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'noValidation', refId: 1, requisiteStageRefIds: []},
          {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];
        pipeline.triggers = [
          {type: 'alsoNotValidation'}
        ];

        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');
      });

      it('checks parent pipeline triggers for match', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = {
          stages: [ { type: 'withValidationIncludingParent', refId: 1 } ],
          triggers: [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        };
        validate();
        expect(pipelineConfigService.getPipelinesForApplication).toHaveBeenCalledWith('someApp');
        expect(messages.length).toBe(0);
      });

      it('caches pipeline configs', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = {
          stages: [ { type: 'withValidationIncludingParent', refId: 1 } ],
          triggers: [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        };

        validate();
        expect(pipelineConfigService.getPipelinesForApplication.calls.count()).toBe(1);

        validate();
        expect(pipelineConfigService.getPipelinesForApplication.calls.count()).toBe(1);

        pipelineConfigValidator.clearCache();
        validate();
        expect(pipelineConfigService.getPipelinesForApplication.calls.count()).toBe(2);
      });

      it('fails if own stages and parent pipeline triggers do not match', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'not-prereq' } ] },
          { id: 'other', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = {
          stages: [ { type: 'withValidationIncludingParent', refId: 1 } ],
          triggers: [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        };
        validate();
        expect(pipelineConfigService.getPipelinesForApplication).toHaveBeenCalledWith('someApp');
        expect(messages.length).toBe(1);
      });

      it('does not check parent triggers unless specified in validator', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = {
          stages: [ { type: 'withValidation', refId: 1 } ],
          triggers: [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        };
        validate();
        expect(pipelineConfigService.getPipelinesForApplication.calls.count()).toBe(0);
        expect(messages.length).toBe(1);
      });
    });

    describe('stageBeforeType', () => {
      it('fails if no stage is first or not preceded by declared stage type', () => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

        pipeline = {
          stages: [
            {type: 'withValidation', refId: 1, requisiteStageRefIds: []},
            {type: 'no-validation', refId: 2, requisiteStageRefIds: []}
          ]
        };

        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages = [
          {type: 'wrongType', refId: 1, requisiteStageRefIds: []},
          {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];

        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'prereq';
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'prereq', refId: 1, requisiteStageRefIds: []},
          {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        validate();
        expect(messages.length).toBe(0);
      });

      it('validates against multiple types if present', () => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

        pipeline = {
          stages: [
            {type: 'three', refId: 1, requisiteStageRefIds: []},
            {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
          ]
        };

        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'one';
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'two', refId: 1, requisiteStageRefIds: []},
          {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        validate();
        expect(messages.length).toBe(0);
      });
    });

    describe('checkRequiredField', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

      it('non-nested field', () => {
        pipeline = { stages: [
          { type: 'simpleField' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo');

        pipeline.stages[0].foo = 4;
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = 0;
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = '';
        validate();
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = null;
        validate();
        expect(messages.length).toBe(1);
      });

      it('nested field', () => {
        pipeline = { stages: [
          { type: 'nestedField' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo.bar.baz');

        pipeline.stages[0].foo = 4;
        validate();
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: 1 };
        validate();
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: null }};
        validate();
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: '' }};
        validate();
        expect(messages.length).toBe(1);

        pipeline.stages[0].foo = { bar: { baz: 0 }};
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages[0].foo = { bar: { baz: 'ok' }};
        validate();
        expect(messages.length).toBe(0);
      });

      it('empty array', () => {
        pipeline = { stages: [
          { type: 'simpleField', foo: [] }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a foo');

        pipeline.stages[0].foo.push(1);
        validate();
        expect(messages.length).toBe(0);
      });
    });

    describe('targetImpedance', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
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

      it('flags when no deploy step present', () => {
        pipeline = { stages: [
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('passes without stack or details', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes with stack', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes with freeFormDetails', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', freeFormDetails: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck--main' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes with stack and freeFormDetails', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main-foo' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes single region', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes multiple regions in same deploy stage', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }},
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1', 'us-west-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('passes multiple regions scattered across deploy stages', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1', 'us-west-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(0);
      });

      it('flags credentials mismatch', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'prod', stack: 'main', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags region mismatch', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch - no stack or freeFormDetails', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck2' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch on stack', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', stack: 'staging', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });

      it('flags cluster mismatch on freeFormDetails', () => {
        pipeline = { stages: [
          { type: 'deploy', clusters: [
            { application: 'deck', account: 'test', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
          ]},
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck--bar' }
        ]};
        validate();
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('mismatch detected');
      });
    });

    describe('skipValidation', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.returnValue({
          validators: [
            {
              type: 'custom',
              validator: function(pipeline, stage, validator, config, messages) {
                pipeline.validationCalled = true;
                messages.push('did not skip');
              },
              skipValidation: function(pipeline, stage) {
                return stage.name === 'skip';
              }
            },
          ]
        });
      });

      it('skips validation if skipValidation method returns true', () => {
        pipeline = { validationCalled: false, stages: [ { name: 'skip' }]};
        validate();
        expect(messages).toEqual([]);
        expect(pipeline.validationCalled).toBe(false);
      });

      it('calls validation if skipValidation method returns false', () => {
        pipeline = { validationCalled: false, stages: [ { name: 'not skip' }]};
        validate();
        expect(messages).toEqual(['did not skip']);
        expect(pipeline.validationCalled).toBe(true);
      });
    });

    describe('custom validator', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage) => {
          if (stage.type === 'targetCheck') {
            return {
              validators: [
                {
                  type: 'custom',
                  validator: function(pipeline, stage, validator, config, messages) {
                    if (stage.name.includes(' ')) {
                      messages.push('No spaces in targetCheck stage names');
                    }
                  }
                },
              ]
            };
          }
          return {};
        });
      });

      it('calls custom validator', () => {
        pipeline = { stages: [
          { type: 'targetCheck', name: 'goodName' }
        ]};
        validate();
        expect(messages.length).toBe(0);

        pipeline.stages[0].name = 'bad name';
        validate();
        expect(messages).toEqual(['No spaces in targetCheck stage names']);

      });
    });
  });

});
