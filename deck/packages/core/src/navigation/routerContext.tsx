import type { RawParams, StateDeclaration, StateService, UIRouter } from '@uirouter/core';
import { useCurrentStateAndParams, useRouter } from '@uirouter/react';
import React from 'react';
import { Observable } from 'rxjs';

export interface IRouterInjectedProps {
  router: UIRouter;
  stateParams: RawParams;
  stateService: StateService;
}

export interface IRouterStateChange {
  to: StateDeclaration;
  from: StateDeclaration;
  toParams: RawParams;
  fromParams: RawParams;
}

export function withRouter<P extends IRouterInjectedProps>(
  Component: React.ComponentType<P>,
): React.ForwardRefExoticComponent<
  React.PropsWithoutRef<React.PropsWithChildren<Omit<P, keyof IRouterInjectedProps>>> & React.RefAttributes<any>
> {
  type OuterProps = React.PropsWithChildren<Omit<P, keyof IRouterInjectedProps>>;
  const RouterContextWrapper = React.forwardRef<any, OuterProps>((props, ref) => {
    const router = useRouter();
    const { params } = useCurrentStateAndParams();
    const RoutedComponent = Component as any;
    return (
      <RoutedComponent {...props} ref={ref} router={router} stateParams={params} stateService={router.stateService} />
    );
  });
  RouterContextWrapper.displayName = `withRouter(${Component.displayName || Component.name || 'Component'})`;
  return RouterContextWrapper;
}

export function stateChangeSuccess$(router: UIRouter): Observable<IRouterStateChange> {
  return new Observable((subscriber) =>
    router.transitionService.onSuccess({}, (transition) =>
      subscriber.next({
        to: transition.to(),
        toParams: transition.params('to'),
        from: transition.from(),
        fromParams: transition.params('from'),
      }),
    ),
  );
}

export function locationChangeSuccess$(router: UIRouter): Observable<string> {
  return new Observable((subscriber) =>
    router.transitionService.onSuccess({}, () => subscriber.next(window.location.href)),
  );
}
