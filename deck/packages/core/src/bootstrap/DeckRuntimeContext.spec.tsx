import { mount } from 'enzyme';
import React from 'react';

import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import {
  IDeckRuntimeServicesInjectedProps,
  useDeckRuntimeServices,
  withDeckRuntimeServices,
} from './DeckRuntimeContext';

describe('DeckRuntimeContext service access', () => {
  it('returns the services owned by the nearest runtime', () => {
    const runtime = createDeckRuntime();
    let services: ReturnType<typeof useDeckRuntimeServices>;
    const Consumer = () => {
      services = useDeckRuntimeServices();
      return null;
    };

    mount(
      <DeckRuntimeContext.Provider value={runtime}>
        <Consumer />
      </DeckRuntimeContext.Provider>,
    );

    expect(services).toBe(runtime.services);
    runtime.dispose();
  });

  it('throws a clear error outside a runtime provider', () => {
    spyOn(React, 'useContext').and.returnValue(null);

    expect(() => useDeckRuntimeServices()).toThrowError(
      'Deck runtime services are unavailable outside DeckRuntimeContext',
    );
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
    const wrapper = mount(
      <DeckRuntimeContext.Provider value={runtime}>
        <WrappedConsumer ref={ref} label="runtime services" />
      </DeckRuntimeContext.Provider>,
    );

    expect(wrapper.find(ServiceConsumer).props()).toEqual(
      jasmine.objectContaining({ label: 'runtime services', deckRuntimeServices: runtime.services }),
    );
    expect(ref.current).toEqual(jasmine.any(ServiceConsumer));
    runtime.dispose();
  });
});
