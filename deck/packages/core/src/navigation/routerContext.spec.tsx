import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { UIRouterRxPlugin } from '@uirouter/rx';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { IRouterInjectedProps } from './routerContext';
import { locationChangeSuccess$, stateChangeSuccess$, withRouter } from './routerContext';

describe('direct router context', () => {
  const routers: UIRouterReact[] = [];

  function createRouter(): UIRouterReact {
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.plugin(UIRouterRxPlugin);
    router.stateRegistry.register({ name: 'root', url: '/root?value' });
    router.stateRegistry.register({ name: 'root.child', url: '/child' });
    routers.push(router);
    return router;
  }

  afterEach(() => routers.splice(0).forEach((router) => router.dispose()));

  it('injects the active state service and reactively updates params from router context', async () => {
    interface IProbeProps extends IRouterInjectedProps {
      values: string[];
    }
    class Probe extends React.Component<IProbeProps> {
      public render(): React.ReactNode {
        const { stateParams, stateService, values } = this.props;
        values.push(stateParams.value);
        return <a href={stateService.href('.child')}>{stateService.includes('root') ? stateParams.value : ''}</a>;
      }
    }
    const RoutedProbe = withRouter(Probe);
    const router = createRouter();
    const values: string[] = [];
    await router.stateService.go('root', { value: 'first' }, { location: false });
    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <RoutedProbe values={values} />
      </UIRouterContext.Provider>,
    );

    expect(wrapper.find('a').prop('href')).toContain('/root/child');
    expect(wrapper.find('a').text()).toBe('first');

    await act(async () => {
      await router.stateService.go('.', { value: 'second' }, { location: false, reload: true });
    });
    wrapper.update();

    expect(values).toContain('second');
    expect(wrapper.find('a').text()).toBe('second');
    wrapper.unmount();
  });

  it('forwards refs to routed class components', () => {
    class Probe extends React.Component<IRouterInjectedProps> {
      public value = 'probe';

      public render(): React.ReactNode {
        return null;
      }
    }
    const RoutedProbe = withRouter(Probe);
    const router = createRouter();
    const ref = React.createRef<Probe>();

    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <RoutedProbe ref={ref} />
      </UIRouterContext.Provider>,
    );

    expect(ref.current?.value).toBe('probe');
    wrapper.unmount();
  });

  it('maps successful transitions to the legacy payload without observing after unsubscribe', async () => {
    const router = createRouter();
    const changes: any[] = [];
    const locations: string[] = [];
    const stateSubscription = stateChangeSuccess$(router).subscribe((change) => changes.push(change));
    const locationSubscription = locationChangeSuccess$(router).subscribe((location) => locations.push(location));

    await router.stateService.go('root', { value: 'first' }, { location: false });

    expect(changes[0].to.name).toBe('root');
    expect(changes[0].toParams.value).toBe('first');
    expect(changes[0].fromParams).toEqual({});
    expect(locations[0]).toBe(window.location.href);

    stateSubscription.unsubscribe();
    locationSubscription.unsubscribe();
    await router.stateService.go('.', { value: 'second' }, { location: false, reload: true });

    expect(changes).toHaveSize(1);
    expect(locations).toHaveSize(1);
  });

  it('observes only the explicitly configured router after disposal and reconfiguration', async () => {
    const firstRouter = createRouter();
    const firstChanges: string[] = [];
    const firstSubscription = stateChangeSuccess$(firstRouter).subscribe(({ to }) => firstChanges.push(to.name));
    await firstRouter.stateService.go('root', {}, { location: false });

    firstSubscription.unsubscribe();
    firstRouter.dispose();
    const secondRouter = createRouter();
    const secondChanges: string[] = [];
    const secondSubscription = stateChangeSuccess$(secondRouter).subscribe(({ to }) => secondChanges.push(to.name));
    await secondRouter.stateService.go('root.child', {}, { location: false });

    expect(firstChanges).toEqual(['root']);
    expect(secondChanges).toEqual(['root.child']);
    secondSubscription.unsubscribe();
  });
});
