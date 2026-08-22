import type { UIRouterReact } from '@uirouter/react';

let directRouter: UIRouterReact | null = null;

export function setDirectRouter(router: UIRouterReact | null): void {
  directRouter = router;
}

export function getDirectRouter(): UIRouterReact | null {
  return directRouter;
}
