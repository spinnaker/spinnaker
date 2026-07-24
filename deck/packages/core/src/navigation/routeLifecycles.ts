import type { UIRouterReact } from '@uirouter/react';

import type { RoutingState } from './RoutingState';
import type { RuntimePageTitleService } from '../bootstrap/DeckRuntimeServices';
import { recordRecentHistory } from '../history/recentHistory.service';

export function registerRouteLifecycles(
  router: UIRouterReact,
  pageTitleService: RuntimePageTitleService,
  routingState: RoutingState,
): () => void {
  const deregisterRoutingState = router.transitionService.onStart({}, (transition) => {
    const finish = routingState.begin();
    pageTitleService.handleRoutingStart(transition);
    transition.promise.then(finish, (error) => {
      finish();
      pageTitleService.handleRoutingError(error, transition);
    });
  }) as () => void;
  const deregisterRoutingSuccess = router.transitionService.onSuccess({}, (transition) => {
    pageTitleService.handleRoutingSuccess(transition.to(), transition);
    recordRecentHistory(transition.to() as any, transition.params('to'));
  }) as () => void;
  let active = true;

  return () => {
    if (!active) {
      return;
    }
    active = false;
    deregisterRoutingState();
    deregisterRoutingSuccess();
  };
}
