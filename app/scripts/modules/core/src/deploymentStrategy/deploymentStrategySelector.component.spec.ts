import { IComponentControllerService, mock } from 'angular';
import { DeploymentStrategyRegistry, IDeploymentStrategy } from 'core/deploymentStrategy';
import {
  DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT,
  DeploymentStrategySelectorController,
  IDeploymentCommand
} from './deploymentStrategySelector.component';

describe('Controller: deploymentStrategySelector', () => {

  beforeEach(
    mock.module(DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT)
  );

  beforeEach(mock.inject(($componentController: IComponentControllerService) => {
    $componentControllerService = $componentController;
  }));

  let $ctrl: DeploymentStrategySelectorController,
      $componentControllerService: IComponentControllerService,
      deployCommand: IDeploymentCommand;

  const strategies: IDeploymentStrategy[] = [
    {
      key: '',
      label: '',
      description: '',
    },
    {
      key: 'no-extra-fields',
      label: '',
      description: '',
    },
    {
      key: 'extra-fields-1',
      label: '',
      description: '',
      additionalFields: ['fieldA'],
      additionalFieldsTemplateUrl: 'aaa'
    },
    {
      key: 'extra-fields-2',
      label: '',
      description: '',
      additionalFields: ['fieldA'],
      additionalFieldsTemplateUrl: 'bbb'
    },
  ];

  const initializeController = (command: IDeploymentCommand) => {
    deployCommand = command;
    $ctrl = $componentControllerService('deploymentStrategySelector', {}, { command }) as DeploymentStrategySelectorController;
    spyOn(DeploymentStrategyRegistry, 'listStrategies').and.returnValue(strategies);
    spyOn(DeploymentStrategyRegistry, 'getStrategy').and.callFake((key: string) => strategies.find(s => s.key === key));
  };

  describe('changing strategies', () => {

    it('removes previous fields when switching strategies if new strategy does not also have the field', () => {
      const command = { strategy: 'extra-fields-1', fieldA: true };
      initializeController(command);
      $ctrl.$onInit();
      expect(command.fieldA).not.toBeUndefined();

      // change to strategy that also has the field
      command.strategy = 'extra-fields-2';
      $ctrl.selectStrategy();
      expect(command.fieldA).not.toBeUndefined();

      // change to strategy that does not have the field
      command.strategy = 'no-extra-fields';
      $ctrl.selectStrategy();
      expect(command.fieldA).toBeUndefined();
    });

    it('removes template when not present', () => {
      const command = { strategy: '' };
      initializeController(command);
      $ctrl.$onInit();
      expect($ctrl.additionalFieldsTemplateUrl).toBeUndefined();

      // change to strategy that has a template
      command.strategy = 'extra-fields-2';
      $ctrl.selectStrategy();
      expect($ctrl.additionalFieldsTemplateUrl).toBe('bbb');

      // change to strategy that does not have a template
      command.strategy = 'no-extra-fields';
      $ctrl.selectStrategy();
      expect($ctrl.additionalFieldsTemplateUrl).toBeUndefined();
    });
  });
});
