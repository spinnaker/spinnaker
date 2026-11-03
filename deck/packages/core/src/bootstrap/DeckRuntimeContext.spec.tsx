import { render, screen } from '@testing-library/react';
import React from 'react';

import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import {
  IDeckRuntimeServicesInjectedProps,
  useDeckRuntimeServices,
  withDeckRuntimeServices,
} from './DeckRuntimeContext';
import { renderHookHarness } from '../utils/testUtils/hookHarness';

describe('DeckRuntimeContext service access', () => {
  it('returns the services owned by the nearest runtime', () => {
    const runtime = createDeckRuntime();
    const { result } = renderHookHarness(
      () => useDeckRuntimeServices(),
      {},
      {
        wrapper: ({ children }) => (
          <DeckRuntimeContext.Provider value={runtime}>{children}</DeckRuntimeContext.Provider>
        ),
      },
    );

    expect(result.current).toBe(runtime.services);
    runtime.dispose();
  });

  it('throws a clear error outside a runtime provider', () => {
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);

    expect(() => renderHookHarness(() => useDeckRuntimeServices(), {})).toThrowError(
      'Deck runtime services are unavailable outside DeckRuntimeContext',
    );
    consoleError.mockRestore();
  });

  it('injects runtime services into class components and forwards refs', () => {
    const runtime = createDeckRuntime();
    const ref = React.createRef<ServiceConsumer>();

    interface IProps extends IDeckRuntimeServicesInjectedProps {
      label: string;
    }

    class ServiceConsumer extends React.Component<IProps> {
      public render() {
        return <span>{this.props.label}</span>;
      }
    }

    const WrappedConsumer = withDeckRuntimeServices(ServiceConsumer);
    render(
      <DeckRuntimeContext.Provider value={runtime}>
        <WrappedConsumer ref={ref} label="runtime services" />
      </DeckRuntimeContext.Provider>,
    );

    expect(screen.getByText('runtime services')).toBeInTheDocument();
    expect(ref.current?.props.deckRuntimeServices).toBe(runtime.services);
    expect(ref.current).toEqual(expect.any(ServiceConsumer));
    runtime.dispose();
  });
});
