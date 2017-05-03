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

  static get $inject() { return ['$rootScope']; }

  constructor(private $rootScope: IRootScopeService) {
    const onChangeSuccess = (_event: IAngularEvent, to: StateDeclaration, toParams: object, from: StateDeclaration, fromParams: object) => {
      this.stateChangeSuccess.next({ to, toParams, from, fromParams });
    };
    this.$rootScope.$on('$stateChangeSuccess', onChangeSuccess);
  }
}

export const STATE_EVENTS = 'spinnaker.core.state.events';
export let stateEvents: StateEvents = undefined;

module(STATE_EVENTS, [])
  .service('stateEvents', StateEvents)
  .run(['stateEvents', (_stateEvents: StateEvents) => stateEvents = _stateEvents])
  .run(['$trace', ($trace: any) => $trace.enable(1)]);
