import { auto, module } from 'angular';
import { IStateParamsService, IStateService } from 'angular-ui-router';

export let $state: IStateService = undefined;
export let $stateParams: IStateParamsService = undefined;

export const UIROUTER_IMPORTS = 'core/uirouter';
module(UIROUTER_IMPORTS, [])
  .run(['$injector', ($injector: auto.IInjectorService) => {
    $state = $injector.get('$state') as IStateService;
    $stateParams = $injector.get('$stateParams') as IStateParamsService;
  }]);
