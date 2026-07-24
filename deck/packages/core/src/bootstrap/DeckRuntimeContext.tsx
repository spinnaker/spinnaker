import React from 'react';

import type { DeckRuntime } from './DeckRuntime';
import type { DeckRuntimeServices } from './DeckRuntimeServices';

export type DeckRuntimeContextValue = Pick<DeckRuntime, 'services'>;

export const DeckRuntimeContext = React.createContext<DeckRuntimeContextValue | null>(null);

export interface IDeckRuntimeServicesInjectedProps {
  deckRuntimeServices: DeckRuntimeServices;
}

export function useDeckRuntimeServices(): DeckRuntimeServices {
  const runtime = React.useContext(DeckRuntimeContext);
  if (!runtime) {
    throw new Error('Deck runtime services are unavailable outside DeckRuntimeContext');
  }

  return runtime.services;
}

export function withDeckRuntimeServices<P extends IDeckRuntimeServicesInjectedProps>(
  Component: React.ComponentType<P>,
): React.ForwardRefExoticComponent<
  React.PropsWithoutRef<React.PropsWithChildren<Omit<P, keyof IDeckRuntimeServicesInjectedProps>>> &
    React.RefAttributes<any>
> {
  type OuterProps = React.PropsWithChildren<Omit<P, keyof IDeckRuntimeServicesInjectedProps>>;
  const DeckRuntimeServicesWrapper = React.forwardRef<any, OuterProps>((props, ref) => {
    const deckRuntimeServices = useDeckRuntimeServices();
    const ServiceComponent = Component as any;
    return <ServiceComponent {...props} ref={ref} deckRuntimeServices={deckRuntimeServices} />;
  });
  DeckRuntimeServicesWrapper.displayName = `withDeckRuntimeServices(${
    Component.displayName || Component.name || 'Component'
  })`;
  return DeckRuntimeServicesWrapper;
}
