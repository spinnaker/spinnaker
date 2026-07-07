import { shallow } from 'enzyme';
import React from 'react';

import {
  AccountTag,
  CollapsibleSection,
  ConsoleOutputLink,
  InstanceDetailsHeader,
  InstanceLinks,
  InstanceReader,
  ManifestReader,
  RecentHistoryService,
} from '@spinnaker/core';

import type { IKubernetesInstanceDetailsProps } from './KubernetesInstanceDetails';
import { KubernetesInstanceActions, KubernetesInstanceDetails } from './KubernetesInstanceDetails';
import { findKubernetesInstanceIdentifier } from './kubernetesInstanceDetails.utils';
import { AnnotationCustomSections } from '../../manifest/AnnotationCustomSections';
import { ManifestLabels } from '../../manifest/ManifestLabels';
import { ManifestQos } from '../../manifest/ManifestQos';
import { ManifestResources } from '../../manifest/ManifestResources';
import { ManifestEvents } from '../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';
import { ManifestCondition } from '../../manifest';

describe('findKubernetesInstanceIdentifier', () => {
  it('finds pod instances under server groups and records server group recent-history data', () => {
    const addRecentHistory = jasmine.createSpy('addRecentHistory');
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
    const addRecentHistory = jasmine.createSpy('addRecentHistory');
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
    const addRecentHistory = jasmine.createSpy('addRecentHistory');
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
    const addRecentHistory = jasmine.createSpy('addRecentHistory');

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
  let props: IKubernetesInstanceDetailsProps;

  beforeEach(() => {
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

    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(Promise.resolve(instanceDetails()) as any);
    spyOn(ManifestReader, 'getManifest').and.returnValue(Promise.resolve(manifestDetails()) as any);
    spyOn(RecentHistoryService, 'addExtraDataToLatest');
  });

  it('loads instance and manifest details before rendering the React sections', async () => {
    const component = shallow(<KubernetesInstanceDetails {...props} />);

    await settle();
    component.update();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-abc123-def45');
    expect(ManifestReader.getManifest).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-abc123-def45');
    expect(RecentHistoryService.addExtraDataToLatest).toHaveBeenCalledWith('instances', {
      account: 'k8s-local',
      region: 'dev',
      serverGroup: 'replicaSet backend-abc123',
    });
    expect(component.find(InstanceDetailsHeader).prop('instanceId')).toBe('backend-abc123-def45');
    expect(component.find(CollapsibleSection).map((section) => section.prop('heading'))).toEqual([
      'Information',
      'Status',
      'Events',
      'Resources',
      'Labels',
    ]);
    expect(component.find(AccountTag).prop('account')).toBe('k8s-local');
    expect(component.find(ManifestQos).prop('manifest')).toEqual(manifestDetails().manifest);
    expect(component.find(ManifestCondition).length).toBe(1);
    expect(component.find(ManifestEvents).prop('manifest')).toEqual(manifestDetails());
    expect(component.find(ManifestResources).prop('metrics')).toEqual(manifestDetails().metrics);
    expect(component.find(ManifestLabels).prop('manifest')).toEqual(manifestDetails().manifest);
    expect(component.find(AnnotationCustomSections).prop('manifest')).toEqual(manifestDetails().manifest);
    expect(component.find(AnnotationCustomSections).prop('resource')).toEqual(
      jasmine.objectContaining({
        account: 'k8s-local',
        name: 'pod backend-abc123-def45',
        provider: 'kubernetes',
      }),
    );
    expect(component.find(InstanceLinks).prop('application')).toBe(props.app);
    expect(component.find(InstanceLinks).prop('environment')).toBe('test');
    expect(component.find(InstanceLinks).prop('address')).toBe('backend.example.com');
    expect(component.find(ConsoleOutputLink).prop('usesMultiOutput')).toBe(true);
    expect(component.find(KubernetesInstanceActions).prop('app')).toBe(props.app);
    expect(component.find(KubernetesInstanceActions).prop('manifest')).toEqual(manifestDetails());
    expect(component.find(KubernetesInstanceActions).prop('instance')).toEqual(
      jasmine.objectContaining({
        account: 'k8s-local',
        name: 'pod backend-abc123-def45',
        provider: 'kubernetes',
      }),
    );
    expect(informationSectionText(component)).not.toContain('Node IP');
    expect(informationSectionText(component)).not.toContain('Pod IP');
  });

  it('auto-closes when the instance cannot be found in application infrastructure', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    const component = shallow(
      <KubernetesInstanceDetails
        {...props}
        app={appWithInfrastructure({ serverGroups: [], loadBalancers: [] })}
        autoClose={autoClose}
      />,
    );

    await settle();
    component.update();

    expect(autoClose).toHaveBeenCalled();
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();
  });

  it('waits for application data before loading changed instance props', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    const ready = deferred<void>();
    const serverGroups: any[] = [];
    const app = appWithInfrastructure({ serverGroups });
    app.ready = () => ready.promise;
    const component = shallow(<KubernetesInstanceDetails {...props} app={app} autoClose={autoClose} />);

    component.setProps({ instance: { instanceId: 'new-pod-uid' } });
    await settle();

    expect(autoClose).not.toHaveBeenCalled();
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();

    serverGroups.push(
      instanceManager({
        instances: [{ id: 'new-pod-uid', name: 'pod backend-new' }],
      }),
    );
    ready.resolve();
    await settle();
    component.update();

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('k8s-local', 'dev', 'pod backend-new');
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

    (InstanceReader.getInstanceDetails as jasmine.Spy).and.callFake(
      (_account: string, _namespace: string, name: string) =>
        name === 'pod backend-new' ? newInstance.promise : oldInstance.promise,
    );
    (ManifestReader.getManifest as jasmine.Spy).and.callFake((_account: string, _namespace: string, name: string) =>
      name === 'pod backend-new' ? newManifest.promise : oldManifest.promise,
    );

    const component = shallow(<KubernetesInstanceDetails {...props} />);

    await settle();
    component.setProps({ instance: { instanceId: 'new-pod-uid' } });

    newInstance.resolve(instanceDetails({ displayName: 'backend-new', humanReadableName: 'pod backend-new' }));
    newManifest.resolve(manifestDetails());
    await settle();
    component.update();

    expect(component.find(InstanceDetailsHeader).prop('instanceId')).toBe('backend-new');

    oldInstance.resolve(instanceDetails({ displayName: 'backend-old', humanReadableName: 'pod backend-old' }));
    oldManifest.resolve(manifestDetails());
    await settle();
    component.update();

    expect(component.find(InstanceDetailsHeader).prop('instanceId')).toBe('backend-new');
  });

  it('ignores stale load failures after a newer pod has rendered', async () => {
    const autoClose = jasmine.createSpy('autoClose');
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

    (InstanceReader.getInstanceDetails as jasmine.Spy).and.callFake(
      (_account: string, _namespace: string, name: string) =>
        name === 'pod backend-new' ? newInstance.promise : oldInstance.promise,
    );
    (ManifestReader.getManifest as jasmine.Spy).and.callFake((_account: string, _namespace: string, name: string) =>
      name === 'pod backend-new' ? newManifest.promise : oldManifest.promise,
    );

    const component = shallow(<KubernetesInstanceDetails {...props} />);

    await settle();
    component.setProps({ instance: { instanceId: 'new-pod-uid' } });

    oldInstance.resolve(instanceDetails({ displayName: 'backend-old', humanReadableName: 'pod backend-old' }));
    newInstance.resolve(instanceDetails({ displayName: 'backend-new', humanReadableName: 'pod backend-new' }));
    newManifest.resolve(manifestDetails());
    await settle();
    component.update();

    oldManifest.reject(new Error('stale load failed'));
    await settle();
    component.update();

    expect(autoClose).not.toHaveBeenCalled();
    expect(component.find(InstanceDetailsHeader).prop('instanceId')).toBe('backend-new');
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));

const informationSectionText = (component: any) =>
  shallow(<div>{component.find(CollapsibleSection).at(0).prop('children')}</div>).text();

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
    isStandalone: false,
    ready: () => Promise.resolve(),
    onRefresh: () => () => null,
    getDataSource: (key: string) => ({
      data: key === 'serverGroups' ? serverGroups : loadBalancers,
    }),
    serverGroups: {
      refresh: jasmine.createSpy('refreshServerGroups'),
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

const manifestDetails = () =>
  ({
    account: 'k8s-local',
    metrics: [{ containerName: 'backend', metrics: { 'CPU(cores)': '1', 'MEMORY(bytes)': '1Gi' } }],
    manifest: {
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
  } as any);
