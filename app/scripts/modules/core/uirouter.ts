import { IQService, IRootScopeService } from 'angular';
import { ReactInjector } from 'core/react.module';
import { StateService, StateParams } from 'angular-ui-router';

export let $state: StateService = undefined;
export let $stateParams: StateParams = undefined;

ReactInjector.give(($injector: any) => {
  $state = Object.create($injector.get('$state')) as StateService;
  $stateParams = $injector.get('$stateParams') as StateParams;

  const $rootScope = $injector.get('$rootScope') as IRootScopeService;
  const $q = $injector.get('$q') as IQService;

  const originalGo = $state.go;
  $state.go = function () {
    const args = arguments;
    const deferred = $q.defer();
    const promise = Object.create(deferred);
    promise.promise.transition = null;
    $rootScope.$applyAsync(() => {
      promise.transition = originalGo.apply(this, args).then((r: any) => { promise.resolve(r); });
    });
    return promise.promise;
  };

});
