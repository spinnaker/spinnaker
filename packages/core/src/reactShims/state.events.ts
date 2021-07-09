import { StateDeclaration } from '@uirouter/angularjs';
import { IAngularEvent, IRootScopeService, module } from 'angular';
import { Subject } from 'rxjs';

export interface IStateChange {
  to: StateDeclaration;
  from: StateDeclaration;
  toParams: object;
  fromParams: object;
}

export class StateEvents {
  public stateChangeSuccess: Subject<IStateChange> = new Subject<IStateChange>();
  public locationChangeSuccess: Subject<string> = new Subject<string>();

  public static $inject = ['$rootScope'];
  constructor(private $rootScope: IRootScopeService) {
    const onChangeSuccess = (
      _event: IAngularEvent,
      to: StateDeclaration,
      toParams: object,
      from: StateDeclaration,
      fromParams: object,
    ) => {
      this.stateChangeSuccess.next({ to, toParams, from, fromParams });
    };
    const onLocationChangeSuccess = (_event: IAngularEvent, newUrl: string) => this.locationChangeSuccess.next(newUrl);

    this.$rootScope.$on('$stateChangeSuccess', onChangeSuccess);
    this.$rootScope.$on('$locationChangeSuccess', onLocationChangeSuccess);
  }
}

export const STATE_EVENTS = 'spinnaker.core.state.events';

module(STATE_EVENTS, []).service('stateEvents', StateEvents);
