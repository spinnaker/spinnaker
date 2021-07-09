import { SpelAutocompleteService } from './SpelAutocompleteService';
import { $q } from 'ngimport';
import { ExecutionService } from '../../pipeline';
import { IExecution, IPipeline, IStage } from '../../domain';

describe('SpelAutocompleteService', () => {
  describe('addPipelineInfo', () => {
    it('should not include "cfServiceKey" when "createServiceKey" stage not present', () => {
      const pipeline: IPipeline & IExecution = {
        application: 'app',
        id: 'id',
        index: 1,
        keepWaitingPipelines: false,
        limitConcurrent: false,
        name: 'pipeline',
        stages: [],
        strategy: false,
        triggers: [],
        parameterConfig: [],
      } as IPipeline & IExecution;
      const spelAutocompleteService = new SpelAutocompleteService($q, {} as ExecutionService);

      const results = spelAutocompleteService.buildTextCompleteConfig(pipeline);

      const helperFunctionsConfig = results.find((r) => r.id === 'helper functions');
      const callbackSpy = jasmine.createSpy();
      helperFunctionsConfig.search('cfS', callbackSpy);
      expect(callbackSpy).toHaveBeenCalledWith([]);
    });

    it('should include "cfServiceKey" when "createServiceKey" stage is present', () => {
      const pipeline: IPipeline & IExecution = {
        application: 'app',
        id: 'id',
        index: 1,
        keepWaitingPipelines: false,
        limitConcurrent: false,
        name: 'pipeline',
        stages: [
          {
            name: 'Create Service Key',
            refId: 'ref-id',
            requisiteStageRefIds: [],
            type: 'createServiceKey',
          } as IStage,
        ],
        strategy: false,
        triggers: [],
        parameterConfig: [],
      } as IPipeline & IExecution;
      const spelAutocompleteService = new SpelAutocompleteService($q, {} as ExecutionService);

      const results = spelAutocompleteService.buildTextCompleteConfig(pipeline);

      const helperFunctionsConfig = results.find((r) => r.id === 'helper functions');
      const callbackSpy = jasmine.createSpy();
      helperFunctionsConfig.search('cfS', callbackSpy);
      expect(callbackSpy).toHaveBeenCalledWith(['cfServiceKey']);
    });
  });

  it('should include helper functions that match search parameter', () => {
    const pipeline: IPipeline & IExecution = {
      application: 'app',
      id: 'id',
      index: 1,
      keepWaitingPipelines: false,
      limitConcurrent: false,
      name: 'pipeline',
      stages: [
        {
          name: 'Create Service Key',
          refId: 'ref-id',
          requisiteStageRefIds: [],
          type: 'createServiceKey',
        } as IStage,
      ],
      strategy: false,
      triggers: [],
      parameterConfig: [],
    } as IPipeline & IExecution;
    const spelAutocompleteService = new SpelAutocompleteService($q, {} as ExecutionService);

    const results = spelAutocompleteService.buildTextCompleteConfig(pipeline);

    const helperFunctionsConfig = results.find((r) => r.id === 'helper functions');
    const callbackSpy = jasmine.createSpy();
    helperFunctionsConfig.search('to', callbackSpy);
    expect(callbackSpy).toHaveBeenCalledWith(['toBoolean', 'toFloat', 'toInt', 'toJson', 'toBase64']);
  });
});
