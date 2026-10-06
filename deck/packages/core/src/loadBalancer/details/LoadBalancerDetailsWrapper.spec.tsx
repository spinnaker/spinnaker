import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import { act, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { LoadBalancerDetailsWrapper } from './LoadBalancerDetailsWrapper';
import type { IUseDetailsHookProps, UseDetailsResult } from './LoadBalancerDetailsWrapper';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('LoadBalancerDetailsWrapper', () => {
  const app = ({ loadBalancers: { refresh: vi.fn() } } as any) as Application;
  const loadBalancer = {
    accountId: 'test',
    name: 'lb-1',
    provider: 'aws',
    region: 'us-east-1',
    vpcId: 'vpc-1',
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

  const renderDetails = (params = loadBalancer) =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: 'root.details', context: router.stateRegistry.get('root.details') as any }}
        >
          <LoadBalancerDetailsWrapper app={app} loadBalancer={params} />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  const element = (params = loadBalancer) => (
    <UIRouterContext.Provider value={router}>
      <UIViewContext.Provider value={{ fqn: 'root.details', context: router.stateRegistry.get('root.details') as any }}>
        <LoadBalancerDetailsWrapper app={app} loadBalancer={params} />
      </UIViewContext.Provider>
    </UIRouterContext.Provider>
  );

  const fetchedLoadBalancer = (name: string, provider = 'aws') =>
    ({
      account: 'test',
      displayName: `${name} display`,
      instanceCounts: { down: 1, outOfService: 2, up: 3 },
      listeners: [{ externalPort: 443, internalPort: 8443, protocol: 'HTTPS' }],
      name,
      provider,
      region: 'us-east-1',
      serverGroups: [{ account: 'test', name: 'app-v001', region: 'us-east-1' }],
      type: provider,
      vpcId: 'vpc-1',
    } as any);

  function registerProvider(
    useDetailsHook: (props: IUseDetailsHookProps) => UseDetailsResult<any>,
    Actions: React.FunctionComponent<any>,
    sections: Array<React.FunctionComponent<any>>,
  ) {
    return vi.spyOn(CloudProviderRegistry, 'getValue').mockImplementation((_provider: string, key: string) => {
      const values: Record<string, any> = {
        'loadBalancer.detailsActions': Actions,
        'loadBalancer.detailsSections': sections,
        'loadBalancer.useDetailsHook': useDetailsHook,
      };
      return values[key];
    });
  }

  it('passes the complete fetched resource and app to actions and sections', async () => {
    const resource = fetchedLoadBalancer('lb-1');
    const hookProps: IUseDetailsHookProps[] = [];
    const actionProps: any[] = [];
    const sectionProps: any[] = [];
    const Actions = (props: any) => {
      actionProps.push(props);
      return <button>Actions for {props.loadBalancer.displayName}</button>;
    };
    const Section = (props: any) => {
      sectionProps.push(props);
      return <div>Section for {props.loadBalancer.listeners[0].protocol}</div>;
    };
    const useDetailsHook = (props: IUseDetailsHookProps) => {
      hookProps.push(props);
      return { data: resource, error: null, loading: false, refetch: () => Promise.resolve() };
    };
    const getValue = registerProvider(useDetailsHook, Actions, [Section]);

    renderDetails();

    expect(await screen.findByRole('heading', { name: 'lb-1 display' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Actions for lb-1 display' })).toBeInTheDocument();
    expect(screen.getByText('Section for HTTPS')).toBeInTheDocument();
    expect(hookProps.at(-1)).toEqual({ app, autoClose: expect.any(Function), loadBalancerParams: loadBalancer });
    expect(hookProps.at(-1)?.loadBalancerParams).toBe(loadBalancer);
    expect(actionProps.at(-1)).toEqual({ app, loadBalancer: resource });
    expect(actionProps.at(-1)?.loadBalancer).toBe(resource);
    expect(sectionProps.at(-1)).toEqual({ app, loadBalancer: resource });
    expect(sectionProps.at(-1)?.loadBalancer).toBe(resource);
    expect(getValue.mock.calls).toEqual([
      ['aws', 'loadBalancer.useDetailsHook'],
      ['aws', 'loadBalancer.detailsActions'],
      ['aws', 'loadBalancer.detailsSections'],
      ['aws', 'cloudProviderLogo'],
    ]);
  });

  it('renders the production loading state while details are pending', async () => {
    const useDetailsHook = () => ({
      data: undefined,
      error: null,
      loading: true,
      refetch: () => Promise.resolve(),
    });
    registerProvider(useDetailsHook, () => null, []);

    const { container } = renderDetails();

    await waitFor(() => expect(container.querySelector('.spinner-container')).toBeInTheDocument());
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('renders the production error state when details fail', async () => {
    const useDetailsHook = () => ({
      data: undefined,
      error: 'Unable to load lb-1',
      loading: false,
      refetch: () => Promise.resolve(),
    });
    registerProvider(useDetailsHook, () => null, []);

    renderDetails();

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load lb-1');
  });

  it('passes a changed resource identity through the existing provider configuration', async () => {
    const nextLoadBalancer = { ...loadBalancer, name: 'lb-2', region: 'us-west-2', vpcId: 'vpc-2' };
    const firstResource = fetchedLoadBalancer('lb-1');
    const nextResource = { ...fetchedLoadBalancer('lb-2'), region: 'us-west-2', vpcId: 'vpc-2' };
    const hookProps: IUseDetailsHookProps[] = [];
    const actionProps: any[] = [];
    const sectionProps: any[] = [];
    const useDetailsHook = (props: IUseDetailsHookProps) => {
      hookProps.push(props);
      const data = props.loadBalancerParams === nextLoadBalancer ? nextResource : firstResource;
      return { data, error: null, loading: false, refetch: () => Promise.resolve() };
    };
    const Actions = (props: any) => {
      actionProps.push(props);
      return <button>Actions for {props.loadBalancer.displayName}</button>;
    };
    const Section = (props: any) => {
      sectionProps.push(props);
      return <div>Section for {props.loadBalancer.vpcId}</div>;
    };
    const getValue = registerProvider(useDetailsHook, Actions, [Section]);
    const rendered = renderDetails();
    expect(await screen.findByRole('heading', { name: 'lb-1 display' })).toBeInTheDocument();

    rendered.rerender(element(nextLoadBalancer));

    expect(await screen.findByRole('heading', { name: 'lb-2 display' })).toBeInTheDocument();
    expect(hookProps.at(-1)?.loadBalancerParams).toBe(nextLoadBalancer);
    expect(actionProps.at(-1)).toEqual({ app, loadBalancer: nextResource });
    expect(sectionProps.at(-1)).toEqual({ app, loadBalancer: nextResource });
    expect(getValue.mock.calls.filter(([, key]) => key.startsWith('loadBalancer.'))).toEqual([
      ['aws', 'loadBalancer.useDetailsHook'],
      ['aws', 'loadBalancer.detailsActions'],
      ['aws', 'loadBalancer.detailsSections'],
    ]);
  });

  it('ignores stale provider configuration that resolves out of order', async () => {
    const requests = new Map<string, IDeferred<any>>();
    const requestFor = (provider: string, key: string) => {
      const request = deferred<any>();
      requests.set(`${provider}:${key}`, request);
      return request.promise;
    };
    const actionProps: any[] = [];
    const AwsActions = () => <button>AWS actions</button>;
    const KubernetesActions = (props: any) => {
      actionProps.push(props);
      return <button>Kubernetes actions for {props.loadBalancer.displayName}</button>;
    };
    const AwsSection = () => <div>AWS section</div>;
    const KubernetesSection = (props: any) => <div>Kubernetes section for {props.loadBalancer.vpcId}</div>;
    const awsResource = fetchedLoadBalancer('aws-lb');
    const kubernetesResource = { ...fetchedLoadBalancer('kubernetes-lb', 'kubernetes'), vpcId: 'kube-vpc' };
    const awsHook = () => ({ data: awsResource, error: null, loading: false, refetch: () => Promise.resolve() });
    const kubernetesHook = () => ({
      data: kubernetesResource,
      error: null,
      loading: false,
      refetch: () => Promise.resolve(),
    });
    const getValue = vi
      .spyOn(CloudProviderRegistry, 'getValue')
      .mockImplementation((provider: string, key: string) =>
        key.startsWith('loadBalancer.') ? requestFor(provider, key) : undefined,
      );
    const rendered = renderDetails();
    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(3));

    const nextLoadBalancer = { ...loadBalancer, name: 'kubernetes-lb', provider: 'kubernetes' };
    rendered.rerender(element(nextLoadBalancer));
    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(6));

    await act(async () => {
      requests.get('kubernetes:loadBalancer.useDetailsHook')?.resolve(kubernetesHook);
      requests.get('kubernetes:loadBalancer.detailsActions')?.resolve(KubernetesActions);
      requests.get('kubernetes:loadBalancer.detailsSections')?.resolve([KubernetesSection]);
      await Promise.resolve();
    });
    expect(await screen.findByRole('heading', { name: 'kubernetes-lb display' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Kubernetes actions for kubernetes-lb display' })).toBeInTheDocument();
    expect(screen.getByText('Kubernetes section for kube-vpc')).toBeInTheDocument();
    expect(actionProps.at(-1)).toEqual({ app, loadBalancer: kubernetesResource });

    await act(async () => {
      requests.get('aws:loadBalancer.useDetailsHook')?.resolve(awsHook);
      requests.get('aws:loadBalancer.detailsActions')?.resolve(AwsActions);
      requests.get('aws:loadBalancer.detailsSections')?.resolve([AwsSection]);
      await Promise.resolve();
    });
    expect(screen.getByRole('heading', { name: 'kubernetes-lb display' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AWS actions' })).not.toBeInTheDocument();
  });

  it('ignores stale detail responses after the resource changes', async () => {
    const detailRequests = new Map<string, IDeferred<any>>([
      ['lb-1', deferred<any>()],
      ['lb-2', deferred<any>()],
    ]);
    const useDeferredDetails = ({ loadBalancerParams }: IUseDetailsHookProps): UseDetailsResult<any> => {
      const [result, setResult] = React.useState<UseDetailsResult<any>>({
        data: undefined,
        error: null,
        loading: true,
        refetch: () => Promise.resolve(),
      });
      React.useEffect(() => {
        let active = true;
        setResult({ data: undefined, error: null, loading: true, refetch: () => Promise.resolve() });
        detailRequests.get(loadBalancerParams.name)?.promise.then((data) => {
          if (active) {
            setResult({ data, error: null, loading: false, refetch: () => Promise.resolve() });
          }
        });
        return () => {
          active = false;
        };
      }, [loadBalancerParams]);
      return result;
    };
    registerProvider(useDeferredDetails, ({ loadBalancer: resource }) => <button>{resource.displayName}</button>, []);
    const rendered = renderDetails();
    await waitFor(() => expect(rendered.container.querySelector('.spinner-container')).toBeInTheDocument());

    const nextLoadBalancer = { ...loadBalancer, name: 'lb-2' };
    rendered.rerender(element(nextLoadBalancer));
    await act(async () => {
      detailRequests.get('lb-1')?.resolve(fetchedLoadBalancer('stale-lb'));
      await Promise.resolve();
    });
    expect(screen.queryByRole('heading', { name: 'stale-lb display' })).not.toBeInTheDocument();
    expect(rendered.container.querySelector('.spinner-container')).toBeInTheDocument();

    const currentResource = fetchedLoadBalancer('lb-2');
    await act(async () => {
      detailRequests.get('lb-2')?.resolve(currentResource);
      await Promise.resolve();
    });
    expect(await screen.findByRole('heading', { name: 'lb-2 display' })).toBeInTheDocument();
  });

  it('renders nothing when provider load balancer details config is missing', async () => {
    const getValue = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue(null);

    const { container } = renderDetails();

    await waitFor(() => expect(getValue).toHaveBeenCalledTimes(3));
    expect(container).toBeEmptyDOMElement();
    expect(getValue.mock.calls).toEqual([
      ['aws', 'loadBalancer.useDetailsHook'],
      ['aws', 'loadBalancer.detailsActions'],
      ['aws', 'loadBalancer.detailsSections'],
    ]);
  });
});
