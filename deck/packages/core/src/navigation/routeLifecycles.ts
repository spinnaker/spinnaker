import type { UIRouterReact } from '@uirouter/react';

import { AngularServices } from '../angular/services';
import { recordRecentHistory } from '../history/recentHistory.service';
import { PageTitleService } from '../pageTitle/pageTitle.service';

export function registerRouteLifecycles(router: UIRouterReact): () => void {
  const pageTitleService = new PageTitleService(
    AngularServices.$rootScope,
    router.globals.params,
    router.transitionService,
  );
  const deregisterRecentHistory = router.transitionService.onSuccess({}, (transition) => {
    recordRecentHistory(transition.to() as any, transition.params('to'));
  }) as () => void;
  let active = true;

  return () => {
    if (!active) {
      return;
    }
    active = false;
    pageTitleService.dispose();
    deregisterRecentHistory();
  };
}
