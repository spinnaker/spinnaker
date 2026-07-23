import { shallow } from 'enzyme';
import React from 'react';

import { PagerComponent } from './Pager';

describe('Pager', () => {
  it('initializes filters from injected route params', () => {
    const component = shallow(
      <PagerComponent
        {...({
          router: {},
          stateParams: {
            app: 'test-app',
            by: 'last',
            direction: 'DESC',
            hideNoApps: true,
            keys: ['service-key'],
            q: 'injected query',
          },
          stateService: {},
        } as any)}
      />,
      { disableLifecycleMethods: true },
    );

    expect(component.state()).toEqual(
      jasmine.objectContaining({
        app: 'test-app',
        filterString: 'injected query',
        hideNoApps: true,
        initialKeys: ['service-key'],
        sortBy: 'last',
        sortDirection: 'DESC',
      }),
    );
  });

  it('updates sorting through the injected state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');

    const component = shallow(
      <PagerComponent {...({ router: {}, stateParams: {}, stateService: { go: injectedGo } } as any)} />,
      { disableLifecycleMethods: true },
    );

    (component.instance() as PagerComponent).sort({ sortBy: 'last', sortDirection: 'DESC' });

    expect(injectedGo).toHaveBeenCalledWith('.', { by: 'last', direction: 'DESC' });
  });

  it('updates selected service keys through the injected state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');
    const component = shallow(
      <PagerComponent {...({ router: {}, stateParams: {}, stateService: { go: injectedGo } } as any)} />,
      { disableLifecycleMethods: true },
    );
    const service = { integration_key: 'service-key' } as any;

    (component.instance() as any).selectedChanged(service, true);

    expect(injectedGo).toHaveBeenCalledWith('.', { keys: ['service-key'] });
  });

  it('updates application visibility through the injected state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');
    const component = shallow(
      <PagerComponent {...({ router: {}, stateParams: {}, stateService: { go: injectedGo } } as any)} />,
      { disableLifecycleMethods: true },
    );

    (component.instance() as any).handleHideNoAppsChanged({ target: { checked: true } });

    expect(injectedGo).toHaveBeenCalledWith('.', { hideNoApps: true });
  });
});
