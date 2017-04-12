import {IStateParamsService, IStateService} from 'angular-ui-router';

export let stateService: IStateService = undefined;
export let stateParamsService: IStateParamsService = undefined;
export const StateServiceInject = ($injector: any) => {
  stateService = <IStateService>$injector.get('$state');
  stateParamsService = <IStateParamsService>$injector.get('$stateParams');
};
