import { act, render, screen, waitFor } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';
import { of, Subject } from 'rxjs';

import { ServerGroupDetailsComponent } from './ServerGroupDetails';
import { ServerGroupDetailsWrapper } from './ServerGroupDetailsWrapper';
import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('ServerGroupDetailsWrapper', () => {
  const app = ({
    serverGroups: {
      onRefresh: vi.fn().mockReturnValue(() => undefined),
      refresh: vi.fn(),
    },
  } as any) as Application;
  const serverGroup = {
    accountId: 'test',
    name: 'deck-v001',
    provider: 'aws',
    region: 'us-east-1',
  };
  let router: UIRouterReact;

  beforeEach(async () => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.stateRegistry.register({ name: 'root', url: '/root' });
    router.stateRegistry.register({ name: 'root.details', url: '/details' });
    await router.stateService.go('root.details', {}, { location: false });
  });

  afterEach(() => router.dispose());

  const withRouter = (children: React.ReactNode) => (
    <UIRouterContext.Provider value={router}>
      <UIViewContext.Provider value={{ fqn: 'root.details', context: router.stateRegistry.get('root.details') as any }}>
        {children}
      </UIViewContext.Provider>
    </UIRouterContext.Provider>
  );

  const resolvedServerGroup = (name: string, type: string) =>
    ({
      account: 'test',
      buildInfo: { images: [] },
      capacity: { desired: 3, max: 5, min: 2 },
      cloudProvider: type,
      instanceCounts: { down: 1, outOfService: 1, up: 2 },
      insightActions: [],
      instances: [{ healthState: 'Up', id: `${name}-instance`, zone: 'us-east-1a' }],
      launchConfig: { imageId: 'ami-123', instanceType: 'm5.large' },
      name,
      provider: type,
      region: 'us-east-1',
      runningExecutions: [],
      runningTasks: [],
      type,
    } as any);

  it('renders React server group details when provider React config is available', async () => {
    const resource = resolvedServerGroup('resolved-v001', 'aws');
    const actionProps: any[] = [];
    const sectionProps: any[] = [];
    const Actions = (props: any) => {
      actionProps.push(props);
      return <button>Actions for {props.serverGroup.launchConfig.instanceType}</button>;
    };
    const Section = (props: any) => {
      sectionProps.push(props);
      return <div>Section capacity {props.serverGroup.capacity.desired}</div>;
    };
    const detailsGetter = vi.fn().mockReturnValue(of(resource));
    const values: Record<string, any> = {
      'serverGroup.detailsActions': Actions,
      'serverGroup.detailsGetter': detailsGetter,
      'serverGroup.detailsSections': [Section],
    };
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((_provider: string, key: string) => values[key]);

    render(withRouter(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />));

    expect(await screen.findByRole('heading', { name: 'resolved-v001' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Actions for m5.large' })).toBeInTheDocument();
    expect(screen.getByText('Section capacity 3')).toBeInTheDocument();
    expect(actionProps.at(-1)).toEqual({ app, serverGroup: resource });
    expect(actionProps.at(-1)?.serverGroup).toBe(resource);
    expect(sectionProps.at(-1)).toEqual({ app, serverGroup: resource });
    expect(sectionProps.at(-1)?.serverGroup).toBe(resource);
    const getterProps = detailsGetter.mock.lastCall[0];
    expect(getterProps).toEqual({
      Actions,
      app,
      detailsGetter,
      router,
      sections: [Section],
      serverGroup,
      stateParams: router.globals.params,
      stateService: router.stateService,
    });
    expect(detailsGetter.mock.lastCall[1]).toEqual(expect.any(Function));
    expect(getValue.mock.calls.filter(([, key]) => key.startsWith('serverGroup.'))).toEqual([
      ['aws', 'serverGroup.detailsActions'],
      ['aws', 'serverGroup.detailsGetter'],
      ['aws', 'serverGroup.detailsSections'],
    ]);
  });

  it('renders nothing when provider server group details config is missing', async () => {
    const getValue = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(undefined);

    const { container } = render(withRouter(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />));

    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(3));
    expect(container).toBeEmptyDOMElement();
  });

  it('switches provider configuration before rendering the next server group', async () => {
    const AwsActions = () => <button>AWS actions</button>;
    const KubernetesActions = () => <button>Kubernetes actions</button>;
    const AwsSection = () => <div>AWS section</div>;
    const KubernetesSection = () => <div>Kubernetes section</div>;
    const awsGetter = vi.fn().mockReturnValue(of(resolvedServerGroup('aws-details', 'aws')));
    const kubernetesGetter = vi.fn().mockReturnValue(of(resolvedServerGroup('kubernetes-details', 'kubernetes')));
    const requests = new Map<string, IDeferred<any>>();
    const requestFor = (provider: string, key: string) => {
      const id = `${provider}:${key}`;
      const request = deferred<any>();
      requests.set(id, request);
      return request.promise;
    };
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((provider: string, key: string) =>
        key.startsWith('serverGroup.') ? requestFor(provider, key) : undefined,
      );
    const { rerender } = render(withRouter(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />));
    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(3));

    const nextServerGroup = { ...serverGroup, name: 'deck-v002', provider: 'kubernetes' };
    rerender(withRouter(<ServerGroupDetailsWrapper app={app} serverGroup={nextServerGroup} />));
    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(6));
    expect(screen.queryByRole('button')).not.toBeInTheDocument();

    await act(async () => {
      requests.get('kubernetes:serverGroup.detailsActions')?.resolve(KubernetesActions);
      requests.get('kubernetes:serverGroup.detailsGetter')?.resolve(kubernetesGetter);
      requests.get('kubernetes:serverGroup.detailsSections')?.resolve([KubernetesSection]);
      await Promise.resolve();
    });
    expect(await screen.findByRole('heading', { name: 'kubernetes-details' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Kubernetes actions' })).toBeInTheDocument();
    expect(screen.getByText('Kubernetes section')).toBeInTheDocument();

    await act(async () => {
      requests.get('aws:serverGroup.detailsActions')?.resolve(AwsActions);
      requests.get('aws:serverGroup.detailsGetter')?.resolve(awsGetter);
      requests.get('aws:serverGroup.detailsSections')?.resolve([AwsSection]);
      await Promise.resolve();
    });
    expect(screen.getByRole('heading', { name: 'kubernetes-details' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AWS actions' })).not.toBeInTheDocument();
    expect(getValue.mock.calls.filter(([, key]) => key.startsWith('serverGroup.'))).toEqual([
      ['aws', 'serverGroup.detailsActions'],
      ['aws', 'serverGroup.detailsGetter'],
      ['aws', 'serverGroup.detailsSections'],
      ['kubernetes', 'serverGroup.detailsActions'],
      ['kubernetes', 'serverGroup.detailsGetter'],
      ['kubernetes', 'serverGroup.detailsSections'],
    ]);
  });

  it('cancels old provider details when the server group and getter change', () => {
    const oldUpdates = new Subject<any>();
    const newUpdates = new Subject<any>();
    const oldGetter = vi.fn().mockReturnValue(oldUpdates);
    const newGetter = vi.fn().mockReturnValue(newUpdates);
    const OldActions = ({ serverGroup: details }: any) => <button>Old actions for {details.name}</button>;
    const NewActions = ({ serverGroup: details }: any) => <button>New actions for {details.name}</button>;
    const go = vi.fn();
    const stateService = { go, params: {} };
    const oldProps = {
      Actions: OldActions,
      app,
      detailsGetter: oldGetter,
      router: {},
      sections: [],
      serverGroup,
      stateParams: {},
      stateService,
    } as any;
    const component = (props: any) => withRouter(<ServerGroupDetailsComponent {...props} />);
    const rendered = render(component(oldProps));

    act(() => oldUpdates.next(resolvedServerGroup('old-details', 'aws')));
    expect(screen.getByRole('heading', { name: 'old-details' })).toBeInTheDocument();
    const oldAutoClose = oldGetter.mock.lastCall[1];

    const nextProps = {
      ...oldProps,
      Actions: NewActions,
      detailsGetter: newGetter,
      serverGroup: { ...serverGroup, name: 'deck-v002', provider: 'kubernetes' },
    };
    rendered.rerender(component(nextProps));

    expect(newGetter).toHaveBeenCalledWith(nextProps, expect.any(Function));
    expect(screen.queryByRole('heading', { name: 'old-details' })).not.toBeInTheDocument();

    act(() => oldUpdates.next(resolvedServerGroup('stale-details', 'aws')));
    expect(screen.queryByRole('heading', { name: 'stale-details' })).not.toBeInTheDocument();

    act(() => newUpdates.next(resolvedServerGroup('new-details', 'kubernetes')));
    expect(screen.getByRole('heading', { name: 'new-details' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New actions for new-details' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Old actions/ })).not.toBeInTheDocument();

    oldAutoClose();
    expect(go).not.toHaveBeenCalled();

    const newAutoClose = newGetter.mock.lastCall[1];
    newAutoClose();
    expect(stateService.params).toEqual({ allowModalToStayOpen: true });
    expect(go).toHaveBeenCalledWith('^', null, { location: 'replace' });

    go.mockClear();
    rendered.unmount();
    newAutoClose();
    expect(go).not.toHaveBeenCalled();
  });

  it('keeps the current actions mounted while refreshing the same server group', () => {
    const updates = new Subject<any>();
    const detailsGetter = vi.fn().mockReturnValue(updates);
    const unmountActions = vi.fn();
    const Actions = () => {
      React.useEffect(() => unmountActions, []);
      return <button>Actions</button>;
    };
    const onRefresh = vi.fn().mockReturnValue(() => undefined);
    const props = {
      Actions,
      app: { serverGroups: { onRefresh } } as any,
      detailsGetter,
      router: {},
      sections: [],
      serverGroup,
      stateParams: {},
      stateService: { go: vi.fn(), params: {} },
    } as any;
    const rendered = render(withRouter(<ServerGroupDetailsComponent {...props} />));
    const details = resolvedServerGroup('deck-v001', 'aws');
    act(() => updates.next(details));

    const actionsButton = screen.getByRole('button', { name: 'Actions' });

    act(() => onRefresh.mock.lastCall[0]());

    expect(detailsGetter).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('heading', { name: 'deck-v001' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Actions' })).toBe(actionsButton);
    expect(unmountActions).not.toHaveBeenCalled();
    rendered.unmount();
  });
});
