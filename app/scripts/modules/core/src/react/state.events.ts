import { module, IRootScopeService, IAngularEvent } from 'angular';
import { StateDeclaration } from 'angular-ui-router';
import { Subject } from 'rxjs/Subject';

export interface IStateChange {
  to: StateDeclaration,
  from: StateDeclaration,
  toParams: object,
  fromParams: object
}

export class StateEvents {
  public stateChangeSuccess: Subject<IStateChange> = new Subject<IStateChange>();

  constructor(private $rootScope: IRootScopeService) {
    'ngInject';
    const onChangeSuccess = (_event: IAngularEvent, to: StateDeclaration, toParams: object, from: StateDeclaration, fromParams: object) => {
      this.stateChangeSuccess.next({ to, toParams, from, fromParams });
    };
    this.$rootScope.$on('$stateChangeSuccess', onChangeSuccess);
  }
}

export const STATE_EVENTS = 'spinnaker.core.state.events';

module(STATE_EVENTS, [])
  .service('stateEvents', StateEvents);
