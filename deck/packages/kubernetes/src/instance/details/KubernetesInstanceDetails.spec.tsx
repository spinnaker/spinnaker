import { act, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { AccountService, InstanceReader, ManifestReader, RecentHistoryService, SETTINGS } from '@spinnaker/core';

import type { IKubernetesInstanceDetailsProps } from './KubernetesInstanceDetails';
import { KubernetesInstanceDetailsComponent as KubernetesInstanceDetails } from './KubernetesInstanceDetails';
import { renderWithRouter } from '../../../../core/src/utils/testUtils/rtl';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';
import { findKubernetesInstanceIdentifier } from './kubernetesInstanceDetails.utils';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

describe('findKubernetesInstanceIdentifier', () => {
  it('finds pod instances under server groups and records server group recent-history data', () => {
    const addRecentHistory = vi.fn();
    const identifier = findKubernetesInstanceIdentifier(
      appWithInfrastructure({
        serverGroups: [
          instanceManager({
            category: 'serverGroup',
            name: 'replicaSet backend-abc123',
            instances: [{ id: 'pod-uid', name: 'pod backend-abc123-def45' }],
          }),
        ],
      }),
      'pod-uid',
      addRecentHistory,
    );

    expect(identifier).toEqual({
      account: 'k8s-local',
      id: 'pod-uid',
      name: 'pod backend-abc123-def45',
      namespace: 'dev',
    });
    expect(addRecentHistory).toHaveBeenCalledWith({
      account: 'k8s-local',
      region: 'dev',
      serverGroup: 'replicaSet backend-abc123',
    });
  });

  it('finds pod instances under load balancers without server group recent-history data', () => {
    const addRecentHistory = vi.fn();
    const identifier = findKubernetesInstanceIdentifier(
      appWithInfrastructure({
        loadBalancers: [
          instanceManager({
            category: 'loadBalancer',
            name: 'service backend',
            instances: [{ id: 'service-pod-uid', name: 'pod backend-from-service' }],
          }),
        ],
      }),
      'service-pod-uid',
      addRecentHistory,
    );

    expect(identifier).toEqual({
      account: 'k8s-local',
      id: 'service-pod-uid',
      name: 'pod backend-from-service',
      namespace: 'dev',
    });
    expect(addRecentHistory).toHaveBeenCalledWith({
      account: 'k8s-local',
      region: 'dev',
    });
  });

  it('finds pod instances under load balancer server groups and records server group recent-history data', () => {
    const addRecentHistory = vi.fn();
    const identifier = findKubernetesInstanceIdentifier(
      appWithInfrastructure({
        loadBalancers: [
          {
            serverGroups: [
              instanceManager({
                category: 'serverGroup',
                name: 'replicaSet backend-from-service',
                instances: [{ id: 'nested-pod-uid', name: 'pod backend-from-nested-service' }],
              }),
            ],
          },
        ],
      }),
      'nested-pod-uid',
      addRecentHistory,
    );

    expect(identifier).toEqual({
      account: 'k8s-local',
      id: 'nested-pod-uid',
      name: 'pod backend-from-nested-service',
      namespace: 'dev',
    });
    expect(addRecentHistory).toHaveBeenCalledWith({
      account: 'k8s-local',
      region: 'dev',
      serverGroup: 'replicaSet backend-from-service',
    });
  });

  it('returns null for missing instances without recording recent-history data', () => {
    const addRecentHistory = vi.fn();

    expect(
      findKubernetesInstanceIdentifier(
        appWithInfrastructure({
          serverGroups: [instanceManager({ instances: [{ id: 'other-pod', name: 'pod other' }] })],
        }),
        'missing-pod',
        addRecentHistory,
      ),
    ).toBeNull();
    expect(addRecentHistory).not.toHaveBeenCalled();
  });
});

describe('<KubernetesInstanceDetails />', () => {
  let originalAdHocInfraWritesEnabled: boolean;
  let props: IKubernetesInstanceDetailsProps;

  beforeEach(() => {
    originalAdHocInfraWritesEnabled = SETTINGS.kubernetesAdHocInfraWritesEnabled;
    SETTINGS.kubernetesAdHocInfraWritesEnabled = true;
    props = {
      app: appWithInfrastructure({
        serverGroups: [
          instanceManager({
            category: 'serverGroup',
            name: 'replicaSet backend-abc123',
            instances: [{ id: 'pod-uid', name: 'pod backend-abc123-def45' }],
          }),
        ],
      }),
      environment: 'test',
      instance: { instanceId: 'pod-uid' },
      moniker: { app: 'kubernetesapp', cluster: 'deployment backend' },
    } as IKubernetesInstanceDetailsProps;

    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(Promise.resolve(instanceDetails()) as any);
    vi.spyOn(ManifestReader, 'getManifest').mockReturnValue(Promise.resolve(manifestDetails()) as any);
    vi.spyOn(RecentHistoryService, 'addExtraDataToLatest').mockReturnValue(undefined);
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
  });

  afterEach(() => {
    SETTINGS.kubernetesAdHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('loads instance and manifest details before rendering the React sections', async () => {
    const user = setupUser();
    const loadedInstance = instanceDetails();
    const loadedManifest = manifestDetails({
      events: [
        {
          apiVersion: 'v1',
          count: 1,
          kind: 'Event',
          lastTimestamp: '2025-07-24T01:27:29Z',
          message: 'Created pod: backend-abc123-def45',
          reason: 'SuccessfulCreate',
          type: 'Normal',
        },
      ],
    });
    loadedManifest.manifest.metadata.annotations = {
      'custom.details.spinnaker.io/resource': '{{account}} {{name}} {{provider}}',
    };
    const builtCommand = { command: 'edit-pod' } as any;
    (InstanceReader.getInstanceDetails as Mock).mockResolvedValue(loadedInstance);
    (ManifestReader.getManifest as Mock).mockResolvedValue(loadedManifest);
    const buildCommand = vi
      .spyOn(KubernetesManifestCommandBuilder, 'buildNewManifestCommand')
      .mockResolvedValue(builtCommand);
    const showWizard = vi.spyOn(ManifestWizard, 'show').mockReturnValue(undefined);

    renderWithRouter(<KubernetesInstanceDetails {...props} />);

    expect(await screen.findByRole('heading', { name: 'backend-abc123-def45' })).toBeInTheDocument();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-abc123-def45');
    expect(ManifestReader.getManifest).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-abc123-def45');
    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('instances', {
      account: 'k8s-local',
      region: 'dev',
      serverGroup: 'replicaSet backend-abc123',
    });
    ['Information', 'Status', 'Events', 'Resources', 'Labels', 'Instance links'].forEach((heading) => {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
    });
    expect(screen.getByText('k8s-local')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'custom' })).toBeInTheDocument();
    expect(screen.getByText('k8s-local pod backend-abc123-def45 kubernetes')).toBeInTheDocument();
    expect(screen.getByText('BestEffort')).toBeInTheDocument();
    expect(screen.getByText('Ready')).toBeInTheDocument();
    expect(screen.getByText('CPU')).toBeInTheDocument();
    expect(screen.getByText(/app:\s*backend/)).toBeInTheDocument();
    expect(screen.getByText('SuccessfulCreate')).toBeInTheDocument();
    expect(screen.getByText('Created pod: backend-abc123-def45')).toBeInTheDocument();
    await user.click(screen.getByRole('heading', { name: 'Instance links' }));
    expect(screen.getByRole('link', { name: 'Backend endpoint' })).toHaveAttribute(
      'href',
      'http://backend.example.com/test',
    );
    expect(screen.getByRole('button', { name: 'Console Output (Raw)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pod Actions' })).toBeInTheDocument();
    expect(screen.queryByText('Node IP')).not.toBeInTheDocument();
    expect(screen.queryByText('Pod IP')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Pod Actions' }));
    await user.click(screen.getByText('Edit'));

    expect(buildCommand).toHaveBeenCalledWith(
      props.app,
      loadedManifest.manifest,
      loadedInstance.moniker,
      loadedInstance.account,
    );
    await waitFor(() =>
      expect(showWizard).toHaveBeenCalledWith({
        application: props.app,
        command: builtCommand,
        title: 'Edit Manifest',
      }),
    );
  });

  it('renders an Images section with copy-to-clipboard for each manifest artifact', async () => {
    (ManifestReader.getManifest as Mock).mockReturnValue(
      Promise.resolve(
        manifestDetails({
          artifacts: [
            { id: '1', type: 'docker/image', reference: 'gcr.io/project/backend@sha256:abc123' },
            { id: '2', type: 'docker/image', reference: 'gcr.io/project/worker@sha256:def456' },
          ],
        }),
      ) as any,
    );
    renderWithRouter(<KubernetesInstanceDetails {...props} />);

    const imagesHeading = await screen.findByRole('heading', { name: 'Images' });
    const imagesSection = within(imagesHeading.closest('.collapsible-section') as HTMLElement);
    const items = imagesSection.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(imagesSection.getAllByRole('button', { name: 'Copy to clipboard' })).toHaveLength(2);
    expect(imagesSection.getAllByRole('textbox').map((node) => (node as HTMLTextAreaElement).value)).toEqual([
      'gcr.io/project/backend@sha256:abc123',
      'gcr.io/project/worker@sha256:def456',
    ]);
    expect(within(items[0]).getByText('gcr.io/project/backend@sha256:abc123', { selector: 'i' })).toBeInTheDocument();
    expect(within(items[1]).getByText('gcr.io/project/worker@sha256:def456', { selector: 'i' })).toBeInTheDocument();
    expect(imagesSection.getAllByText('docker/image', { exact: false, selector: 'b' })).toHaveLength(2);
  });

  it('omits the Images section when the manifest has no artifacts', async () => {
    renderWithRouter(<KubernetesInstanceDetails {...props} />);

    expect(await screen.findByRole('heading', { name: 'backend-abc123-def45' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Labels' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Images' })).not.toBeInTheDocument();
  });

  it('auto-closes when the instance cannot be found in application infrastructure', async () => {
    const autoClose = vi.fn();
    renderWithRouter(
      <KubernetesInstanceDetails
        {...props}
        app={appWithInfrastructure({ serverGroups: [], loadBalancers: [] })}
        autoClose={autoClose}
      />,
    );

    await waitFor(() => expect(autoClose).toHaveBeenCalled());
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();
  });

  it('replaces missing instance details through the injected state service', () => {
    const stateService = { go: vi.fn(), params: {} };
    const component = new KubernetesInstanceDetails({
      ...props,
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).autoClose();

    expect(stateService.params.allowModalToStayOpen).toBe(true);
    expect(stateService.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
  });

  it('waits for application data before loading changed instance props', async () => {
    const autoClose = vi.fn();
    const ready = deferred<void>();
    const serverGroups: any[] = [];
    const app = appWithInfrastructure({ serverGroups });
    app.ready = () => ready.promise;
    const rendered = renderWithRouter(<KubernetesInstanceDetails {...props} app={app} autoClose={autoClose} />);

    rendered.rerender(
      <KubernetesInstanceDetails {...props} app={app} autoClose={autoClose} instance={{ instanceId: 'new-pod-uid' }} />,
    );
    await act(async () => Promise.resolve());

    expect(autoClose).not.toHaveBeenCalled();
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();

    serverGroups.push(
      instanceManager({
        instances: [{ id: 'new-pod-uid', name: 'pod backend-new' }],
      }),
    );
    await act(async () => ready.resolve());

    await waitFor(() =>
      expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-new'),
    );
  });

  it('keeps the newer pod details when an older load resolves last', async () => {
    const oldInstance = deferred<any>();
    const oldManifest = deferred<any>();
    const newInstance = deferred<any>();
    const newManifest = deferred<any>();
    props = {
      ...props,
      app: appWithInfrastructure({
        serverGroups: [
          instanceManager({
            instances: [
              { id: 'pod-uid', name: 'pod backend-old' },
              { id: 'new-pod-uid', name: 'pod backend-new' },
            ],
          }),
        ],
      }),
    };

    (InstanceReader.getInstanceDetails as Mock).mockImplementation(
      (_account: string, _namespace: string, name: string) =>
        name === 'pod backend-new' ? newInstance.promise : oldInstance.promise,
    );
    (ManifestReader.getManifest as Mock).mockImplementation((_account: string, _namespace: string, name: string) =>
      name === 'pod backend-new' ? newManifest.promise : oldManifest.promise,
    );

    const rendered = renderWithRouter(<KubernetesInstanceDetails {...props} />);
    await waitFor(() => expect(InstanceReader.getInstanceDetails).toHaveBeenCalled());
    rendered.rerender(<KubernetesInstanceDetails {...props} instance={{ instanceId: 'new-pod-uid' }} />);

    await act(async () => {
      newInstance.resolve(instanceDetails({ displayName: 'backend-new', humanReadableName: 'pod backend-new' }));
      newManifest.resolve(manifestDetails());
    });

    expect(await screen.findByRole('heading', { name: 'backend-new' })).toBeInTheDocument();

    await act(async () => {
      oldInstance.resolve(instanceDetails({ displayName: 'backend-old', humanReadableName: 'pod backend-old' }));
      oldManifest.resolve(manifestDetails());
    });

    expect(screen.getByRole('heading', { name: 'backend-new' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'backend-old' })).not.toBeInTheDocument();
  });

  it('ignores stale load failures after a newer pod has rendered', async () => {
    const autoClose = vi.fn();
    const oldInstance = deferred<any>();
    const oldManifest = deferred<any>();
    const newInstance = deferred<any>();
    const newManifest = deferred<any>();
    props = {
      ...props,
      autoClose,
      app: appWithInfrastructure({
        serverGroups: [
          instanceManager({
            instances: [
              { id: 'pod-uid', name: 'pod backend-old' },
              { id: 'new-pod-uid', name: 'pod backend-new' },
            ],
          }),
        ],
      }),
    };

    (InstanceReader.getInstanceDetails as Mock).mockImplementation(
      (_account: string, _namespace: string, name: string) =>
        name === 'pod backend-new' ? newInstance.promise : oldInstance.promise,
    );
    (ManifestReader.getManifest as Mock).mockImplementation((_account: string, _namespace: string, name: string) =>
      name === 'pod backend-new' ? newManifest.promise : oldManifest.promise,
    );

    const rendered = renderWithRouter(<KubernetesInstanceDetails {...props} />);
    await waitFor(() => expect(InstanceReader.getInstanceDetails).toHaveBeenCalled());
    rendered.rerender(<KubernetesInstanceDetails {...props} instance={{ instanceId: 'new-pod-uid' }} />);

    await act(async () => {
      oldInstance.resolve(instanceDetails({ displayName: 'backend-old', humanReadableName: 'pod backend-old' }));
      newInstance.resolve(instanceDetails({ displayName: 'backend-new', humanReadableName: 'pod backend-new' }));
      newManifest.resolve(manifestDetails());
    });
    expect(await screen.findByRole('heading', { name: 'backend-new' })).toBeInTheDocument();

    await act(async () => oldManifest.reject(new Error('stale load failed')));

    expect(autoClose).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: 'backend-new' })).toBeInTheDocument();
  });
});

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });

  return { promise, resolve, reject };
}

const instanceManager = (overrides: any = {}) => ({
  account: 'k8s-local',
  region: 'dev',
  category: 'serverGroup',
  name: 'replicaSet backend',
  instances: [] as any[],
  ...overrides,
});

const appWithInfrastructure = ({
  serverGroups = [],
  loadBalancers = [],
}: {
  serverGroups?: any[];
  loadBalancers?: any[];
}) =>
  ({
    isStandalone: true,
    ready: () => Promise.resolve(),
    onRefresh: () => () => null,
    getDataSource: (key: string) => ({
      data: key === 'serverGroups' ? serverGroups : loadBalancers,
    }),
    serverGroups: {
      refresh: vi.fn(),
    },
    attributes: {
      instanceLinks: [
        {
          title: 'Instance links',
          links: [{ title: 'Backend endpoint', path: 'http://{{ipAddress}}/{{environment}}' }],
        },
      ],
    },
  } as any);

const instanceDetails = (overrides: any = {}) =>
  ({
    account: 'k8s-local',
    apiVersion: 'v1',
    cloudProvider: 'kubernetes',
    createdTime: 1753320449000,
    displayName: 'backend-abc123-def45',
    healthState: 'Up',
    humanReadableName: 'pod backend-abc123-def45',
    kind: 'pod',
    moniker: { app: 'kubernetesapp', cluster: 'deployment backend' },
    namespace: 'dev',
    publicDnsName: 'backend.example.com',
    zone: 'dev',
    ...overrides,
  } as any);

const manifestDetails = (overrides: any = {}) =>
  ({
    account: 'k8s-local',
    metrics: [{ containerName: 'backend', metrics: { 'CPU(cores)': '1', 'MEMORY(bytes)': '1Gi' } }],
    manifest: {
      kind: 'Pod',
      metadata: {
        labels: {
          app: 'backend',
        },
        name: 'backend-abc123-def45',
      },
      spec: {
        containers: [{ name: 'backend' }],
        nodeName: 'worker.dev.example',
      },
      status: {
        conditions: [{ lastTransitionTime: '2025-07-24T01:27:29Z', message: 'ready', status: 'True', type: 'Ready' }],
        hostIP: '10.0.0.1',
        podIP: '10.0.0.2',
        qosClass: 'BestEffort',
      },
    },
    events: [],
    ...overrides,
  } as any);
