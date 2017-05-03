import { ReactInjector } from 'core/react.module';
import { StateService, StateParams } from 'angular-ui-router';

export let $state: StateService = undefined;
export let $stateParams: StateParams = undefined;

ReactInjector.give(($injector: any) => {
  $state = $injector.get('$state') as StateService;
  $stateParams = $injector.get('$stateParams') as StateParams;
});
