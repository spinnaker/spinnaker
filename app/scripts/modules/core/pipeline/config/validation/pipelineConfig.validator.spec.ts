import {mock} from 'angular';
import {PipelineConfigService} from '../services/pipelineConfig.service';
import {
  PIPELINE_CONFIG_VALIDATOR, PipelineConfigValidator,
  IPipelineValidationResults, IStageTypeConfig, IValidatorConfig, ICustomValidator
} from './pipelineConfig.validator';
import {IRequiredFieldValidationConfig} from './requiredField.validator';
import {
  IStageOrTriggerBeforeTypeValidationConfig,
  StageOrTriggerBeforeTypeValidator
} from './stageOrTriggerBeforeType.validator';
import Spy = jasmine.Spy;
import {IStageBeforeTypeValidationConfig} from './stageBeforeType.validator';
import {ITargetImpedanceValidationConfig} from './targetImpedance.validator';
import {IStage} from 'core/domain/IStage';
import {IPipeline} from 'core/domain/IPipeline';
import {IServiceAccountAccessValidationConfig, ITriggerWithServiceAccount} from './serviceAccountAccess.validator';
import {ServiceAccountService} from 'core/serviceAccount/serviceAccount.service';
import IProvideService = angular.auto.IProvideService;

describe('pipelineConfigValidator', () => {

  let pipeline: IPipeline,
      validate: () => void,
      validationResults: IPipelineValidationResults,
      pipelineConfigValidator: PipelineConfigValidator,
      pipelineConfig: PipelineConfigService,
      pipelineConfigService: PipelineConfigService,
      serviceAccountService: ServiceAccountService,
      stageOrTriggerBeforeTypeValidator: StageOrTriggerBeforeTypeValidator,
      $q: ng.IQService;

  function buildPipeline(stages: any[], triggers: any[] = []): IPipeline {
    stages.forEach((stage, idx) => {
      stage.name = stage.name || '' + idx;
      if (!stage.refId) {
        stage.refId = idx;
      }
      if (!stage.requisiteStageRefIds) {
        stage.requisiteStageRefIds = [];
      }
    });
    triggers.forEach(t => t.enabled = true);

    return {
      id: 'a',
      name: 'some pipeline',
      index: 1,
      strategy: false,
      parameterConfig: [],
      application: 'app',
      limitConcurrent: true,
      keepWaitingPipelines: true,
      parallel: true,
      executionEngine: 'v2',
      stages: stages,
      triggers: triggers,
    };
  }

  function buildStageTypeConfig(validators: IValidatorConfig[] = []): IStageTypeConfig {
    return {
      label: null,
      description: null,
      key: null,
      templateUrl: null,
      executionDetailsUrl: null,
      controller: null,
      controllerAs: null,
      validators: validators
    };
  }

  beforeEach(
    mock.module(
      PIPELINE_CONFIG_VALIDATOR,
      require('../pipelineConfig.module.js')
    )
  );

  beforeEach(
    mock.module(($provide: IProvideService) => {
      return $provide.constant('settings', {
        feature: {
          fiatEnabled: true,
        }
      });
  }));

  beforeEach(mock.inject((_pipelineConfigValidator_: PipelineConfigValidator,
                          _pipelineConfig_: PipelineConfigService,
                          _pipelineConfigService_: PipelineConfigService,
                          _serviceAccountService_: ServiceAccountService,
                          _stageOrTriggerBeforeTypeValidator_: StageOrTriggerBeforeTypeValidator,
                          _$q_: ng.IQService,
                          $rootScope: ng.IRootScopeService) => {
    pipelineConfigValidator = _pipelineConfigValidator_;
    pipelineConfig = _pipelineConfig_;
    pipelineConfigService = _pipelineConfigService_;
    serviceAccountService = _serviceAccountService_;
    stageOrTriggerBeforeTypeValidator = _stageOrTriggerBeforeTypeValidator_;
    $q = _$q_;
    validate = () => {
      validationResults = null;
      pipelineConfigValidator.validatePipeline(pipeline).then(result => validationResults = result);
      $rootScope.$new().$digest();
    };
  }));

  describe('validation', () => {

    it('performs validation against stages and triggers where declared, ignores others', () => {
      spyOn(pipelineConfig, 'getTriggerConfig').and.callFake((type: string) => {
        if (type === 'withTriggerValidation') {
          return buildStageTypeConfig([
            {
              type: 'requiredField',
              fieldName: 'boo',
              message: 'boo is required',
            } as IRequiredFieldValidationConfig
          ]);
        }
        return buildStageTypeConfig();
      });
      spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
        if (stage.type === 'withValidation') {
          return buildStageTypeConfig([
              {
                type: 'requiredField',
                fieldName: 'bar',
                message: 'bar is required',
              } as IRequiredFieldValidationConfig
            ]
          );
        }
        return buildStageTypeConfig();
      });

      pipeline = buildPipeline(
        [
          { type: 'withValidation', },
          { type: 'no-validation' },
        ],
        [
          { type: 'withTriggerValidation' },
          { type: 'withoutValidation' },
        ]
      );

      validate();
      expect(validationResults.hasWarnings).toBe(true);
      expect(validationResults.stages.length).toBe(1);
      expect(validationResults.stages[0].messages).toEqual(['bar is required']);
      expect(validationResults.pipeline).toEqual(['boo is required']);
    });

    it('executes all validators', () => {
      spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
        if (stage.type === 'withValidation') {
          return buildStageTypeConfig([
            {
              type: 'requiredField',
              fieldName: 'bar',
              message: 'bar is required',
            } as IRequiredFieldValidationConfig,
            {
              type: 'requiredField',
              fieldName: 'foo',
              message: 'foo is also required'
            } as IRequiredFieldValidationConfig
          ]);
        } else {
          return buildStageTypeConfig();
        }
      });

      pipeline = buildPipeline([
          { type: 'withValidation', },
          { type: 'no-validation' }
        ]);

      validate();
      expect(validationResults.hasWarnings).toBe(true);
      expect(validationResults.stages.length).toBe(1);
      expect(validationResults.stages[0].messages).toEqual(['bar is required', 'foo is also required']);

      pipeline.stages[0]['foo'] = 'a';
      validate();
      expect(validationResults.hasWarnings).toBe(true);
      expect(validationResults.stages[0].messages).toEqual(['bar is required']);
    });

  });

  describe('validators', () => {

    describe('stageOrTriggerBeforeType', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'withValidationIncludingParent') {
            return buildStageTypeConfig([
                {
                  type: 'stageOrTriggerBeforeType',
                  checkParentTriggers: true,
                  stageType: 'prereq',
                  message: 'need a prereq',
                } as IStageOrTriggerBeforeTypeValidationConfig,
              ]);
          }
          if (stage.type === 'withValidation') {
            return buildStageTypeConfig([
                {
                  type: 'stageOrTriggerBeforeType',
                  stageType: 'prereq',
                  message: 'need a prereq',
                } as IStageOrTriggerBeforeTypeValidationConfig,
              ]);
          }
          return buildStageTypeConfig();
        });
        pipeline = buildPipeline(
          [
            {type: 'withValidation', refId: 1, requisiteStageRefIds: []},
            {type: 'no-validation', refId: 2, requisiteStageRefIds: []}
          ]
        );
      });

      it('fails if no stage/trigger is first or not preceded by declared stage type', () => {
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);

        pipeline.stages = [
          {name: 'a', type: 'wrongType', refId: 1, requisiteStageRefIds: []},
          {name: 'b', type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];

        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);
      });

      it('succeeds if preceding stage type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages = [
          {name: 'a', type: 'prereq', refId: 1, requisiteStageRefIds: []},
          {name: 'b', type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
          {name: 'c', type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
        ];

        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('succeeds if trigger type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages = [
          {name: 'a', type: 'withValidation', refId: 1, requisiteStageRefIds: []}
        ];
        pipeline.triggers = [
          {type: 'prereq', enabled: true}
        ];

        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('fails if no preceding stage type matches and no trigger type matches', () => {
        pipeline.stages[0].type = 'prereq';
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages = [
          {name: 'a', type: 'noValidation', refId: 1, requisiteStageRefIds: []},
          {name: 'b', type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
        ];
        pipeline.triggers = [
          {type: 'alsoNotValidation', enabled: true}
        ];

        validate();
        expect(validationResults.hasWarnings).toBe(true);
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);
      });

      it('checks parent pipeline triggers for match', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = buildPipeline(
          [ { type: 'withValidationIncludingParent', refId: 1 } ],
          [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        );
        validate();
        expect(pipelineConfigService.getPipelinesForApplication).toHaveBeenCalledWith('someApp');
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('caches pipeline configs', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = buildPipeline(
          [ { type: 'withValidationIncludingParent', refId: 1 } ],
          [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        );

        validate();
        expect((<Spy>pipelineConfigService.getPipelinesForApplication).calls.count()).toBe(1);

        validate();
        expect((<Spy>pipelineConfigService.getPipelinesForApplication).calls.count()).toBe(1);

        stageOrTriggerBeforeTypeValidator.clearCache();
        validate();
        expect((<Spy>pipelineConfigService.getPipelinesForApplication).calls.count()).toBe(2);
      });

      it('fails if own stages and parent pipeline triggers do not match', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'not-prereq' } ] },
          { id: 'other', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = buildPipeline(
          [ { type: 'withValidationIncludingParent', refId: 1 } ],
          [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        );
        validate();
        expect(pipelineConfigService.getPipelinesForApplication).toHaveBeenCalledWith('someApp');
        expect(validationResults.stages.length).toBe(1);
      });

      it('does not check parent triggers unless specified in validator', () => {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
          { id: 'abcd', triggers: [ { type: 'prereq' } ] }
        ]));

        pipeline = buildPipeline(
          [ { type: 'withValidation', refId: 1 } ],
          [ { type: 'pipeline', application: 'someApp', pipeline: 'abcd' } ]
        );
        validate();
        expect((<Spy>pipelineConfigService.getPipelinesForApplication).calls.count()).toBe(0);
        expect(validationResults.stages.length).toBe(1);
      });
    });

    describe('stageBeforeType', () => {
      it('fails if no stage is first or not preceded by declared stage type', () => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'withValidation') {
            return buildStageTypeConfig([
                {
                  type: 'stageBeforeType',
                  stageType: 'prereq',
                  message: 'need a prereq',
                } as IStageBeforeTypeValidationConfig,
              ]
            );
          } else {
            return buildStageTypeConfig();
          }
        });

        pipeline = buildPipeline(
          [
            {type: 'withValidation', refId: 1, requisiteStageRefIds: []},
            {type: 'no-validation', refId: 2, requisiteStageRefIds: []}
          ]
        );

        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);

        pipeline = buildPipeline(
          [
            {type: 'wrongType', refId: 1, requisiteStageRefIds: []},
            {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
            ]
        );

        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);

        pipeline.stages[0].type = 'prereq';
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline = buildPipeline(
          [
            {type: 'prereq', refId: 1, requisiteStageRefIds: []},
            {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
            {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
          ]
        );

        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('validates against multiple types if present', () => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'withValidation') {
            return buildStageTypeConfig([
                {
                  type: 'stageBeforeType',
                  stageTypes: ['one', 'two'],
                  message: 'need a prereq',
                } as IStageBeforeTypeValidationConfig,
              ]);
          } else {
            return buildStageTypeConfig();
          }
        });

        pipeline = buildPipeline(
          [
            {type: 'three', refId: 1, requisiteStageRefIds: []},
            {type: 'withValidation', refId: 2, requisiteStageRefIds: [1]}
          ]
        );

        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a prereq']);

        pipeline.stages[0].type = 'one';
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline = buildPipeline(
          [
            {type: 'two', refId: 1, requisiteStageRefIds: []},
            {type: 'somethingElse', refId: 2, requisiteStageRefIds: [1]},
            {type: 'withValidation', refId: 3, requisiteStageRefIds: [2]}
          ]
        );

        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });
    });

    describe('checkRequiredField', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'simpleField') {
            return buildStageTypeConfig([
                {
                  type: 'requiredField',
                  fieldName: 'foo',
                  message: 'need a foo',
                } as IRequiredFieldValidationConfig,
              ]
            );
          }
          if (stage.type === 'nestedField') {
            return buildStageTypeConfig([
                {
                  type: 'requiredField',
                  fieldName: 'foo.bar.baz',
                  message: 'need a foo.bar.baz',
                } as IRequiredFieldValidationConfig,
              ]
            );
          }
          return buildStageTypeConfig();
        });
      });

      it('non-nested field', () => {
        pipeline = buildPipeline( [ { type: 'simpleField' } ] );
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a foo']);

        pipeline.stages[0]['foo'] = 4;
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages[0]['foo'] = 0;
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages[0]['foo'] = '';
        validate();
        expect(validationResults.stages.length).toBe(1);

        pipeline.stages[0]['foo'] = null;
        validate();
        expect(validationResults.stages.length).toBe(1);
      });

      it('nested field', () => {
        pipeline = buildPipeline( [ { type: 'nestedField' } ] );
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a foo.bar.baz']);

        pipeline.stages[0]['foo'] = 4;
        validate();
        expect(validationResults.stages.length).toBe(1);

        pipeline.stages[0]['foo'] = { bar: 1 };
        validate();
        expect(validationResults.stages.length).toBe(1);

        pipeline.stages[0]['foo'] = { bar: { baz: null }};
        validate();
        expect(validationResults.stages.length).toBe(1);

        pipeline.stages[0]['foo'] = { bar: { baz: '' }};
        validate();
        expect(validationResults.stages.length).toBe(1);

        pipeline.stages[0]['foo'] = { bar: { baz: 0 }};
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages[0]['foo'] = { bar: { baz: 'ok' }};
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('empty array', () => {
        pipeline = buildPipeline( [ { type: 'simpleField', foo: [] } ] );
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['need a foo']);

        pipeline.stages[0]['foo'].push(1);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });
    });

    describe('targetImpedance', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'targetCheck') {
            return buildStageTypeConfig([
                {
                  type: 'targetImpedance',
                  message: 'mismatch detected',
                } as ITargetImpedanceValidationConfig,
              ]
            );
          }
          return buildStageTypeConfig();
        });
      });

      it('flags when no deploy step present', () => {
        pipeline = buildPipeline([
          { type: 'targetCheck', regions: ['us-east-1'], credentials: 'test', cluster: 'deck-main' }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });

      it('passes without stack or details', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes with stack', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes with freeFormDetails', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', freeFormDetails: 'main', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck--main'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes with stack and freeFormDetails', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main-foo'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes single region', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes multiple regions in same deploy stage', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }},
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1', 'us-west-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('passes multiple regions scattered across deploy stages', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'deploy',
            refId: 2,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 3,
            requisiteStageRefIds: [1, 2],
            regions: ['us-east-1', 'us-west-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);
      });

      it('flags credentials mismatch', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'prod', stack: 'main', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });

      it('flags region mismatch', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'main', availabilityZones: { 'us-west-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });

      it('flags cluster mismatch - no stack or freeFormDetails', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck2'
          }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });

      it('flags cluster mismatch on stack', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', stack: 'staging', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck-main'
          }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });

      it('flags cluster mismatch on freeFormDetails', () => {
        pipeline = buildPipeline([
          {
            type: 'deploy',
            refId: 1,
            clusters: [
              { application: 'deck', account: 'test', freeFormDetails: 'foo', availabilityZones: { 'us-east-1': [] }}
            ]
          },
          {
            type: 'targetCheck',
            refId: 2,
            requisiteStageRefIds: [1],
            regions: ['us-east-1'],
            credentials: 'test',
            cluster: 'deck--bar'
          }
        ]);
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['mismatch detected']);
      });
    });

    describe('skipValidation', () => {
      let validationCalled = false;
      beforeEach(() => {
        validationCalled = false;
        spyOn(pipelineConfig, 'getStageConfig').and.returnValue(buildStageTypeConfig([
            {
              type: 'custom',
              validate: (): string => {
                validationCalled = true;
                return 'did not skip';
              },
              skipValidation: (_p: IPipeline, stage: IStage): boolean => {
                return stage.name === 'skip';
              }
            } as ICustomValidator
          ])
        );
      });

      it('skips validation if skipValidation method returns true', () => {
        pipeline = buildPipeline( [ { name: 'skip' } ] );
        validate();
        expect(validationResults.hasWarnings).toBe(false);
        expect(validationCalled).toBe(false);
      });

      it('calls validation if skipValidation method returns false', () => {
        pipeline = buildPipeline( [ { name: 'not skip' } ] );
        validate();
        expect(validationResults.hasWarnings).toBe(true);
        expect(validationResults.stages[0].messages).toEqual(['did not skip']);
        expect(validationCalled).toBe(true);
      });
    });

    describe('custom validator', () => {
      beforeEach(() => {
        spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
          if (stage.type === 'targetCheck') {
            return buildStageTypeConfig([
                {
                  type: 'custom',
                  validate: (_p: IPipeline, s: IStage): string => {
                    if (s.name.includes(' ')) {
                      return 'No spaces in targetCheck stage names';
                    }
                    return null;
                  }
                } as ICustomValidator,
              ]);
          }
          return buildStageTypeConfig();
        });
      });

      it('calls custom validator', () => {
        pipeline = buildPipeline([
          { type: 'targetCheck', name: 'goodName' }
        ]);
        validate();
        expect(validationResults.hasWarnings).toBe(false);

        pipeline.stages[0].name = 'bad name';
        validate();
        expect(validationResults.stages.length).toBe(1);
        expect(validationResults.stages[0].messages).toEqual(['No spaces in targetCheck stage names']);

      });
    });
  });

  describe('serviceAccountAccess', () => {
    beforeEach(() => {
      spyOn(pipelineConfig, 'getStageConfig').and.callFake((stage: IStage) => {
        if (stage.type === 'targetCheck') {
          return buildStageTypeConfig([
            {
              type: 'serviceAccountAccess',
              message: 'Not allowed!',
            } as IServiceAccountAccessValidationConfig,
          ]);
        }
        return buildStageTypeConfig();
      });

      spyOn(serviceAccountService, 'getServiceAccounts').and.returnValue($q.resolve(['my-account']));
    });

    it('calls service account access validator', () => {
      let trigger = { type: 'targetCheck', name: 'git trigger', runAsUser: 'my-account' } as ITriggerWithServiceAccount;
      pipeline = buildPipeline([trigger]);

      validate();
      expect(validationResults.hasWarnings).toBe(false);

      trigger.runAsUser = 'account-I-do-not-have-access-to';
      validate();
      expect(validationResults.stages.length).toBe(1);
      expect(validationResults.stages[0].messages).toEqual(['Not allowed!']);
    });
  });
});
