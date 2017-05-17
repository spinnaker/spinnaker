import {mock, IComponentControllerService} from 'angular';

import {EXECUTION_STATUS_COMPONENT, ExecutionStatusController} from './executionStatus.component';

describe('Controller: executionStatus', function () {
  let $ctrl: ExecutionStatusController,
      $componentController: IComponentControllerService;

  beforeEach(mock.module((EXECUTION_STATUS_COMPONENT)));

  beforeEach(mock.inject((_$componentController_: IComponentControllerService) => {
    $componentController = _$componentController_;
  }));

  const initialize = (execution: any) => {
    $ctrl = <ExecutionStatusController>$componentController('executionStatus', {}, { execution });
    $ctrl.$onInit();
  };

  describe('parameter extraction', function () {
    it('adds parameters, sorted alphabetically, to vm if present on trigger', function () {
      const execution = {
        trigger: {
          parameters: {
            a: 'b',
            b: 'c',
            d: 'a',
          }
        }
      };
      initialize(execution);
      expect($ctrl.parameters).toEqual([
        {key: 'a', value: 'b'},
        {key: 'b', value: 'c'},
        {key: 'd', value: 'a'}
      ]);
    });

    it('does not add parameters to vm if none present in trigger', function () {
      const execution = { trigger: { } };
      initialize(execution);
      expect($ctrl.parameters).toBeUndefined();
    });

    it('excludes some parameters if the pipeline is a strategy', function () {
      const execution = {
        isStrategy: true,
        trigger: {
          parameters: {
            included: 'a',
            parentPipelineId: 'b',
            strategy: 'c',
            parentStageId: 'd',
            deploymentDetails: 'e',
            cloudProvider: 'f'
          }
        }
      };
      initialize(execution);
      expect($ctrl.parameters).toEqual([
        {key: 'included', value: 'a'}
      ]);
    });
  });

});
