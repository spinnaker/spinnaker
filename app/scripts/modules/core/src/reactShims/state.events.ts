import { module, IRootScopeService, IAngularEvent } from 'angular';
import { StateDeclaration } from '@uirouter/angularjs';
import { Subject } from 'rxjs';

export interface IStateChange {
  to: StateDeclaration;
  from: StateDeclaration;
  toParams: object;
  fromParams: object;
}

export class StateEvents {
  public stateChangeSuccess: Subject<IStateChange> = new Subject<IStateChange>();

  public static $inject = ['$rootScope'];
  constructor(private $rootScope: IRootScopeService) {
    'ngInject';
    const onChangeSuccess = (
      _event: IAngularEvent,
      to: StateDeclaration,
      toParams: object,
      from: StateDeclaration,
      fromParams: object,
    ) => {
      this.stateChangeSuccess.next({ to, toParams, from, fromParams });
    };
    this.$rootScope.$on('$stateChangeSuccess', onChangeSuccess);
  }
}

export const STATE_EVENTS = 'spinnaker.core.state.events';

module(STATE_EVENTS, []).service('stateEvents', StateEvents);
