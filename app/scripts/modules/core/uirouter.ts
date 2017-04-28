import { IStateParamsService, IStateService } from 'angular-ui-router';
import { ReactInjector } from 'core/react.module';

export let $state: IStateService = undefined;
export let $stateParams: IStateParamsService = undefined;

ReactInjector.give(($injector: any) => {
  $state = $injector.get('$state') as IStateService;
  $stateParams = $injector.get('$stateParams') as IStateParamsService;
});
