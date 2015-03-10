'use strict';

describe('pipelineConfigValidator', function() {

  beforeEach(module(
    'deckApp.pipelines.config.validator.service',
    'deckApp.pipelines.config'
  ));

  beforeEach(inject(function(pipelineConfigValidator, pipelineConfig) {
    this.validator = pipelineConfigValidator;
    this.pipelineConfig = pipelineConfig;
  }));

  describe('validation', function() {

    it('performs validation against stages where declared, ignores others', function() {
      spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
        if (type === 'withValidation') {
          return {
            validators: [
              {
                type: 'requiredField',
                fieldName: 'bar',
                message: 'bar is required',
              }
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
      expect(validationMessages.length).toBe(1);
      expect(validationMessages[0]).toBe('bar is required');
    });

    it('executes all validators', function() {
      spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
        if (type === 'withValidation') {
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

  describe('validators', function() {

    describe('stageBeforeType', function() {
      it('fails if no stage is first or not preceded by declared stage type', function() {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
          if (type === 'withValidation') {
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
            {type: 'withValidation'},
            {type: 'no-validation'}
          ]
        };

        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages = [
          {type: 'wrongType'},
          {type: 'withValidation'}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'prereq';
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'prereq'},
          {type: 'somethingElse'},
          {type: 'withValidation'}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });

      it('validates against multiple types if present', function() {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
          if (type === 'withValidation') {
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
            {type: 'three'},
            {type: 'withValidation'}
          ]
        };

        var messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(1);
        expect(messages[0]).toBe('need a prereq');

        pipeline.stages[0].type = 'one';
        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);

        pipeline.stages = [
          {type: 'two'},
          {type: 'somethingElse'},
          {type: 'withValidation'}
        ];

        messages = this.validator.validatePipeline(pipeline);
        expect(messages.length).toBe(0);
      });
    });

    describe('checkRequiredField', function() {
      beforeEach(function() {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
          if (type === 'simpleField') {
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
          if (type === 'nestedField') {
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

      it('non-nested field', function() {
        var pipeline = { stages: [ { type: 'simpleField' }]};
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

      it('nested field', function() {
        var pipeline = { stages: [ { type: 'nestedField' }]};
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
    });

    describe('stageBeforeMethod', function() {
      beforeEach(function() {
        spyOn(this.pipelineConfig, 'getStageConfig').and.callFake(function(type) {
          if (type === 'toValidate') {
            return {
              validators: [
                {
                  type: 'stageBeforeMethod',
                  validate: function(beforeStage, thisStage) {
                    if (beforeStage.check !== thisStage.check) {
                      return 'NOPE';
                    }
                  }
                },
              ]
            };
          }
          return {};
        });
      });

      it('ignores stage if no stage before', function() {
        var pipeline = { stages: [ { type: 'toValidate'} ] };
        expect(this.validator.validatePipeline(pipeline).length).toBe(0);
      });

      it('adds validation message if validator fails', function() {
        var pipeline = { stages: [ { type: 'other'}, { type: 'toValidate', check: 'foo'} ] };
        expect(this.validator.validatePipeline(pipeline).length).toBe(1);
        expect(this.validator.validatePipeline(pipeline)[0]).toBe('NOPE');

        pipeline.stages[0].check = 'bar';
        expect(this.validator.validatePipeline(pipeline).length).toBe(1);

        pipeline.stages[0].check = 'foo';
        expect(this.validator.validatePipeline(pipeline).length).toBe(0);
      });
    });
  });

});
