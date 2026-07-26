import { cloneDeep } from 'lodash';
import React from 'react';

import type {
  Application,
  DeckRuntimeServices,
  IModalComponentProps,
  IRouterInjectedProps,
  IStage,
  IWizardPageInjectedProps,
} from '@spinnaker/core';
import {
  DeckRuntimeContext,
  noop,
  ReactModal,
  TaskMonitor,
  withRouter,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { validateGceServerGroupCommand } from './GceServerGroupWizard.helpers';
import type {
  IGceServerGroupCommand,
  IGceServerGroupWizardAdapter,
  IGceServerGroupWizardCommandState,
} from './GceServerGroupWizard.types';
import { GceServerGroupWizardAdapter } from './GceServerGroupWizardAdapter';
import { createGceServerGroupWizardCommandState } from './GceServerGroupWizardPage';
import {
  AdvancedSettings,
  GceServerGroupFirewalls,
  GceServerGroupLoadBalancers,
  Policies,
  ServerGroupBasicSettings,
  ServerGroupCapacity,
  ServerGroupImageSettings,
  ServerGroupInstanceType,
} from './pages';

interface IGceCloneServerGroupModalProps extends IModalComponentProps {
  adapter?: IGceServerGroupWizardAdapter;
  application: Application;
  command: IGceServerGroupCommand;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  title?: string;
}

interface IGceCloneServerGroupModalState {
  command: IGceServerGroupCommand;
  initializationError: boolean;
  loaded: boolean;
  taskMonitor: TaskMonitor;
}

const GLOBAL_LOAD_BALANCER_NAMES = 'global-load-balancer-names';
const REGIONAL_LOAD_BALANCER_NAMES = 'load-balancer-names';
const BACKEND_SERVICE_NAMES = 'backend-service-names';

interface ILoadBalancerMetadataReference {
  key: typeof GLOBAL_LOAD_BALANCER_NAMES | typeof REGIONAL_LOAD_BALANCER_NAMES;
  names: string[];
}

interface IPersistedSelectionSnapshot {
  autoHealingHealthCheck: any;
  backendServiceMetadata: string[];
  backendServices?: Record<string, string[]>;
  credentials: string;
  distributionZones: string[];
  image: any;
  implicitSecurityGroups: any[];
  instanceType: any;
  loadBalancerMetadata: Record<string, string[]>;
  loadBalancerReferences: Record<string, ILoadBalancerMetadataReference>;
  loadBalancers: any[];
  minCpuPlatform: any;
  network: any;
  region: string;
  regional: boolean;
  securityGroups: string[];
  subnet: any;
  zone: any;
}

function compactMetadata(metadata: Record<string, string[]>): Record<string, string> {
  return Object.keys(metadata).reduce((result: Record<string, string>, key: string) => {
    const values = Array.from(new Set(metadata[key].filter(Boolean)));
    if (values.length) {
      result[key] = values.join(',');
    }
    return result;
  }, {});
}

function loadBalancerName(loadBalancer: any): string {
  return loadBalancer.name || loadBalancer;
}

function metadataValues(value: any): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  return typeof value === 'string' ? value.split(',').map((item) => item.trim()) : [];
}

function metadataSource(command: IGceServerGroupCommand): Record<string, string[]> {
  const loadBalancerMetadata = command.loadBalancerMetadata || {};
  const instanceMetadata = command.instanceMetadata || {};
  return {
    [GLOBAL_LOAD_BALANCER_NAMES]: metadataValues(
      loadBalancerMetadata[GLOBAL_LOAD_BALANCER_NAMES] || instanceMetadata[GLOBAL_LOAD_BALANCER_NAMES],
    ),
    [REGIONAL_LOAD_BALANCER_NAMES]: metadataValues(
      loadBalancerMetadata[REGIONAL_LOAD_BALANCER_NAMES] || instanceMetadata[REGIONAL_LOAD_BALANCER_NAMES],
    ),
  };
}

function loadBalancerMetadataReference(name: string, loadBalancer: any): ILoadBalancerMetadataReference | undefined {
  if (!loadBalancer) {
    return undefined;
  }
  if (loadBalancer.loadBalancerType === 'HTTP' || loadBalancer.loadBalancerType === 'INTERNAL_MANAGED') {
    const names = (loadBalancer.listeners || []).map((listener: any) => listener.name).filter(Boolean);
    return names.length
      ? {
          key: loadBalancer.loadBalancerType === 'HTTP' ? GLOBAL_LOAD_BALANCER_NAMES : REGIONAL_LOAD_BALANCER_NAMES,
          names,
        }
      : undefined;
  }
  return {
    key:
      loadBalancer.loadBalancerType === 'SSL' || loadBalancer.loadBalancerType === 'TCP'
        ? GLOBAL_LOAD_BALANCER_NAMES
        : REGIONAL_LOAD_BALANCER_NAMES,
    names: [name],
  };
}

function buildLoadBalancerMetadata(command: IGceServerGroupCommand): Record<string, string> {
  const loadBalancerIndex = command.backingData?.filtered?.loadBalancerIndex || {};
  const unavailableLoadBalancers = (command.loadBalancers || []).filter(
    (loadBalancer: any) => !loadBalancer.loadBalancerType && !loadBalancerIndex[loadBalancerName(loadBalancer)],
  );
  const persistedMetadata = metadataSource(command);
  const metadata: Record<string, string[]> = {
    [GLOBAL_LOAD_BALANCER_NAMES]: unavailableLoadBalancers.length
      ? [...persistedMetadata[GLOBAL_LOAD_BALANCER_NAMES]]
      : [],
    [REGIONAL_LOAD_BALANCER_NAMES]: unavailableLoadBalancers.length
      ? [...persistedMetadata[REGIONAL_LOAD_BALANCER_NAMES]]
      : [],
  };

  (command.loadBalancers || []).forEach((loadBalancer: any) => {
    const name = loadBalancerName(loadBalancer);
    const loadBalancerDetails = loadBalancer.loadBalancerType ? loadBalancer : loadBalancerIndex[name];
    const reference = loadBalancerMetadataReference(name, loadBalancerDetails);
    if (reference) {
      metadata[reference.key].push(...reference.names);
    }
  });

  const selectedLoadBalancers = new Set((command.loadBalancers || []).map(loadBalancerName));
  if (command.backendServices && Object.keys(command.backendServices).length) {
    metadata[BACKEND_SERVICE_NAMES] = Object.keys(command.backendServices)
      .filter((loadBalancer) => selectedLoadBalancers.has(loadBalancer))
      .flatMap((loadBalancer) => command.backendServices[loadBalancer]);
  } else if (unavailableLoadBalancers.length) {
    metadata[BACKEND_SERVICE_NAMES] = metadataValues(
      command.backendServiceMetadata || command.instanceMetadata?.[BACKEND_SERVICE_NAMES],
    );
  }

  return compactMetadata(metadata);
}

export function transformGceServerGroupCommand(command: IGceServerGroupCommand): IGceServerGroupCommand {
  const transformed = cloneDeep(command);
  const instanceMetadata = { ...(command.instanceMetadata || {}) };
  delete instanceMetadata[GLOBAL_LOAD_BALANCER_NAMES];
  delete instanceMetadata[REGIONAL_LOAD_BALANCER_NAMES];
  delete instanceMetadata[BACKEND_SERVICE_NAMES];

  transformed.instanceMetadata = {
    ...instanceMetadata,
    ...buildLoadBalancerMetadata(command),
  };
  transformed.tags = (command.tags || []).map((tag: any) => tag.value || tag);
  transformed.targetSize = command.capacity?.desired;
  if (command.autoscalingPolicy) {
    transformed.capacity = {
      ...command.capacity,
      max: command.autoscalingPolicy.maxNumReplicas,
      min: command.autoscalingPolicy.minNumReplicas,
    };
  } else if (command.capacity) {
    transformed.capacity = {
      ...command.capacity,
      max: command.capacity.desired,
      min: command.capacity.desired,
    };
  }
  if (transformed.minCpuPlatform === '(Automatic)') {
    transformed.minCpuPlatform = '';
  }
  delete transformed.loadBalancerMetadata;
  delete transformed.securityGroups;
  return transformed;
}

export class GceCloneServerGroupModalComponent extends React.Component<
  IGceCloneServerGroupModalProps & IRouterInjectedProps,
  IGceCloneServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IGceCloneServerGroupModalProps & IRouterInjectedProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private adapter: IGceServerGroupWizardAdapter;
  private command: IGceServerGroupCommand;
  private commandState: IGceServerGroupWizardCommandState;
  private configureRequest = 0;
  private applicationRefreshUnsubscribe?: () => void;
  private formik: IWizardPageInjectedProps<IGceServerGroupCommand>['formik'] = null;
  private unmounted = false;

  public static show(props: IGceCloneServerGroupModalProps, runtimeServices: DeckRuntimeServices): Promise<any> {
    return ReactModal.show(
      GceCloneServerGroupModal,
      props,
      { dialogClassName: 'wizard-modal modal-lg' },
      runtimeServices,
    );
  }

  public constructor(
    props: IGceCloneServerGroupModalProps & IRouterInjectedProps,
    context: React.ContextType<typeof DeckRuntimeContext>,
  ) {
    super(props, context);
    this.adapter =
      props.adapter ||
      (context?.services
        ? GceServerGroupWizardAdapter.fromRuntimeServices(context.services)
        : new GceServerGroupWizardAdapter());
    this.command = cloneDeep(props.command);
    this.commandState = createGceServerGroupWizardCommandState(this.command);
    this.state = {
      command: this.command,
      initializationError: false,
      loaded: Boolean(this.command.backingData?.filtered),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: props.title || 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    void this.configureCommand();
  }

  public componentWillUnmount(): void {
    this.unmounted = true;
    this.configureRequest++;
    this.clearApplicationRefreshSubscription();
  }

  private configureCommand = async (): Promise<void> => {
    const request = ++this.configureRequest;
    try {
      let command = this.formik?.values || this.command;
      if (command.viewState?.requiresTemplateSelection) {
        const baseCommand = await this.adapter.buildNewServerGroupCommand(this.props.application, {
          mode: 'createPipeline',
        });
        command = initializePipelineCreateCommand(baseCommand, command);
      }
      if (this.unmounted || request !== this.configureRequest) {
        return;
      }

      const configured = await this.adapter.configureCommand(this.props.application, command);
      if (this.unmounted || request !== this.configureRequest) {
        return;
      }

      const latestCommand = this.formik?.values || command;
      const persistedSelections = snapshotPersistedSelections(latestCommand);
      const refreshedCommand = mergeRefreshedCommand(latestCommand, configured);
      initializeCommand(refreshedCommand, persistedSelections);
      restoreUnavailableSelections(refreshedCommand, persistedSelections);
      this.command = refreshedCommand;
      this.commandState.command = refreshedCommand;
      this.commandState.formikValues = refreshedCommand;
      this.formik?.setValues(refreshedCommand);
      this.setState({ command: refreshedCommand, initializationError: false, loaded: true });
    } catch (_error) {
      if (!this.unmounted && request === this.configureRequest) {
        this.setState({ initializationError: true, loaded: false });
      }
    }
  };

  private retryConfiguration = (): Promise<void> => {
    this.setState({ initializationError: false, loaded: false });
    return this.configureCommand();
  };

  private submit = (command: IGceServerGroupCommand = this.formik?.values || this.command): any => {
    const transformed = transformGceServerGroupCommand(command);
    const mode = transformed.viewState?.mode;
    if (this.props.forPipelineConfig || mode === 'createPipeline' || mode === 'editPipeline') {
      return this.props.closeModal(transformed);
    }
    return this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.cloneServerGroup(transformed as any, this.props.application),
    );
  };

  private onTaskComplete = (): void => {
    this.props.application.serverGroups.refresh();
    this.applicationRefreshUnsubscribe = this.props.application.serverGroups.onNextRefresh(
      null,
      this.onApplicationRefresh,
    );
  };

  private onApplicationRefresh = (): void => {
    this.clearApplicationRefreshSubscription();
    if (this.unmounted) {
      return;
    }

    const command = this.formik?.values || this.command;
    const cloneStage = this.state.taskMonitor.task?.execution?.stages?.find(
      (stage: IStage) => stage.type === 'cloneServerGroup',
    );
    const newServerGroupName = cloneStage?.context?.['deploy.server.groups']?.[command.region];
    if (!newServerGroupName) {
      return;
    }

    let transitionTo = '^.^.^.clusters.serverGroup';
    if (this.props.stateService.includes('**.clusters.serverGroup')) {
      transitionTo = '^.serverGroup';
    }
    if (this.props.stateService.includes('**.clusters.cluster.serverGroup')) {
      transitionTo = '^.^.serverGroup';
    }
    if (this.props.stateService.includes('**.clusters')) {
      transitionTo = '.serverGroup';
    }
    this.props.stateService.go(transitionTo, {
      accountId: command.credentials,
      provider: 'gce',
      region: command.region,
      serverGroup: newServerGroupName,
    });
  };

  private clearApplicationRefreshSubscription = (): void => {
    this.applicationRefreshUnsubscribe?.();
    this.applicationRefreshUnsubscribe = undefined;
  };

  public render(): JSX.Element {
    const { application, dismissModal, title } = this.props;
    const { command, initializationError, loaded, taskMonitor } = this.state;

    if (initializationError) {
      return (
        <div className="modal-content gce-server-group-initialization-error">
          <div className="modal-header">
            <button aria-label="Close" className="close" onClick={dismissModal} type="button">
              <span aria-hidden="true">&times;</span>
            </button>
            <h3>{title || 'Create Server Group'}</h3>
          </div>
          <div className="modal-body">
            <div className="alert alert-danger" role="alert">
              Unable to load the resources required to configure this server group. Check your connection and try again.
            </div>
          </div>
          <div className="modal-footer">
            <button
              className="btn btn-default gce-server-group-initialization-close"
              onClick={dismissModal}
              type="button"
            >
              Close
            </button>
            <button className="btn btn-primary" onClick={this.retryConfiguration} type="button">
              Retry
            </button>
          </div>
        </div>
      );
    }

    return (
      <WizardModal<IGceServerGroupCommand>
        key={loaded ? 'loaded' : 'loading'}
        closeModal={this.submit}
        dismissModal={dismissModal}
        heading={title || 'Create Server Group'}
        initialValues={command}
        loading={!loaded}
        submitButtonLabel={command.viewState?.submitButtonLabel || 'Done'}
        taskMonitor={taskMonitor}
        validate={validateGceServerGroupCommand}
        render={({ formik, nextIdx, wizard }) => {
          this.formik = formik;
          this.command = formik.values;
          const pageProps = { adapter: this.adapter, app: application, commandState: this.commandState, formik };
          return (
            <>
              <WizardPage
                label="Basic Settings"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <ServerGroupBasicSettings ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Image"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <ServerGroupImageSettings ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Instance Type"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <ServerGroupInstanceType ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Capacity/Distribution"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <ServerGroupCapacity ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Load Balancers"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <GceServerGroupLoadBalancers ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Firewalls"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <GceServerGroupFirewalls ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Policies"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <Policies ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Advanced Settings"
                order={nextIdx()}
                render={({ innerRef, onLoadingChanged }) => (
                  <AdvancedSettings ref={innerRef} {...pageProps} onLoadingChanged={onLoadingChanged} />
                )}
                wizard={wizard}
              />
            </>
          );
        }}
      />
    );
  }
}

export const GceCloneServerGroupModal = Object.assign(withRouter(GceCloneServerGroupModalComponent), {
  show: GceCloneServerGroupModalComponent.show,
});

function initializeCommand(command: IGceServerGroupCommand, snapshot: IPersistedSelectionSnapshot): void {
  const accountAvailable =
    !snapshot.credentials || referenceValues(command.backingData?.accounts, ['name']).includes(snapshot.credentials);
  if (!accountAvailable) {
    return;
  }

  command.credentialsChanged?.(command);
  const regionAvailable =
    !snapshot.region || referenceValues(command.backingData?.filtered?.regions, ['name']).includes(snapshot.region);
  if (!regionAvailable) {
    return;
  }

  ['regionalChanged', 'regionChanged', 'networkChanged', 'zoneChanged', 'customInstanceChanged'].forEach((handler) =>
    command[handler]?.(command),
  );
}

function snapshotPersistedSelections(command: IGceServerGroupCommand): IPersistedSelectionSnapshot {
  const loadBalancers = cloneDeep(command.loadBalancers || []);
  const loadBalancerIndex = command.backingData?.filtered?.loadBalancerIndex || {};
  return {
    autoHealingHealthCheck: command.autoHealingPolicy
      ? cloneDeep({
          healthCheck: command.autoHealingPolicy.healthCheck,
          healthCheckKind: command.autoHealingPolicy.healthCheckKind,
          healthCheckUrl: command.autoHealingPolicy.healthCheckUrl,
        })
      : undefined,
    backendServiceMetadata: metadataValues(command.backendServiceMetadata),
    backendServices: command.backendServices ? cloneDeep(command.backendServices) : undefined,
    credentials: command.credentials,
    distributionZones: cloneDeep(command.distributionPolicy?.zones || []),
    image: command.viewState?.imageId || command.image,
    implicitSecurityGroups: cloneDeep(command.implicitSecurityGroups || []),
    instanceType: command.instanceType,
    loadBalancerMetadata: cloneDeep(metadataSource(command)),
    loadBalancerReferences: loadBalancers.reduce(
      (references: Record<string, ILoadBalancerMetadataReference>, loadBalancer: any) => {
        const name = loadBalancerName(loadBalancer);
        const reference = loadBalancerMetadataReference(
          name,
          loadBalancer.loadBalancerType ? loadBalancer : indexedLoadBalancer(loadBalancerIndex, name),
        );
        if (reference) {
          references[name] = reference;
        }
        return references;
      },
      {},
    ),
    loadBalancers,
    minCpuPlatform: command.minCpuPlatform,
    network: command.network,
    region: command.region,
    regional: command.regional,
    securityGroups: cloneDeep(command.securityGroups || []),
    subnet: command.subnet,
    zone: command.zone,
  };
}

function restoreUnavailableSelections(command: IGceServerGroupCommand, snapshot: IPersistedSelectionSnapshot): void {
  const filtered = command.backingData?.filtered || {};
  if (!referenceValues(command.backingData?.accounts, ['name']).includes(snapshot.credentials)) {
    command.credentials = snapshot.credentials;
  }
  if (!referenceValues(filtered.regions, ['name']).includes(snapshot.region)) {
    command.region = snapshot.region;
  }
  if (snapshot.instanceType && !metadataValues(filtered.instanceTypes).includes(snapshot.instanceType)) {
    command.instanceType = snapshot.instanceType;
  }
  if (snapshot.minCpuPlatform && !metadataValues(filtered.cpuPlatforms).includes(snapshot.minCpuPlatform)) {
    command.minCpuPlatform = snapshot.minCpuPlatform;
  }
  if (
    snapshot.image &&
    !referenceValues(command.backingData?.allImages, ['imageName', 'name', 'id']).includes(snapshot.image)
  ) {
    command.image = snapshot.image;
    command.viewState.imageId = snapshot.image;
  }
  if (snapshot.network && !referenceValues(filtered.networks, ['id', 'name']).includes(snapshot.network)) {
    command.network = snapshot.network;
  }
  if (snapshot.subnet && !referenceValues(filtered.subnets, ['id', 'name']).includes(snapshot.subnet)) {
    command.subnet = snapshot.subnet;
  }
  if (!snapshot.regional && snapshot.zone && !metadataValues(filtered.zones).includes(snapshot.zone)) {
    command.zone = snapshot.zone;
  }
  if (snapshot.regional) {
    const unavailableZones = snapshot.distributionZones.filter(
      (zone) => !metadataValues(filtered.zones).includes(zone),
    );
    command.distributionPolicy = {
      ...command.distributionPolicy,
      zones: unique([...(command.distributionPolicy?.zones || []), ...unavailableZones]),
    };
  }

  const firewallProperties = ['id', 'name'];
  const availableFirewalls = new Set([
    ...referenceValues(filtered.securityGroups, firewallProperties),
    ...referenceValues(command.implicitSecurityGroups, firewallProperties),
  ]);
  command.securityGroups = unique([
    ...(command.securityGroups || []),
    ...snapshot.securityGroups.filter((firewall) => !availableFirewalls.has(firewall)),
  ]);
  command.implicitSecurityGroups = uniqueByReference(
    [
      ...(command.implicitSecurityGroups || []),
      ...snapshot.implicitSecurityGroups.filter(
        (firewall) => !availableFirewalls.has(referenceValue(firewall, firewallProperties)),
      ),
    ],
    firewallProperties,
  );

  const healthCheck = snapshot.autoHealingHealthCheck;
  const healthCheckAvailable = (filtered.healthChecks || []).some(
    (option: any) => option.name === healthCheck?.healthCheck && option.kind === healthCheck?.healthCheckKind,
  );
  if (healthCheck?.healthCheck && !healthCheckAvailable) {
    command.autoHealingPolicy = {
      ...(command.autoHealingPolicy || {}),
      ...healthCheck,
    };
  }

  const loadBalancerIndex = filtered.loadBalancerIndex || {};
  const unavailableLoadBalancers = snapshot.loadBalancers.filter(
    (loadBalancer) => !indexedLoadBalancer(loadBalancerIndex, loadBalancerName(loadBalancer)),
  );
  const unavailableNames = new Set(unavailableLoadBalancers.map(loadBalancerName));
  command.loadBalancers = uniqueByName([...(command.loadBalancers || []), ...unavailableLoadBalancers]);

  const backendServices = cloneDeep(command.backendServices || {});
  unavailableNames.forEach((name) => {
    if (snapshot.backendServices?.[name]) {
      backendServices[name] = snapshot.backendServices[name];
    }
  });
  if (Object.keys(backendServices).length) {
    command.backendServices = backendServices;
  } else {
    delete command.backendServices;
  }
  const knownBackendServices = Object.keys(snapshot.backendServices || {})
    .filter((name) => !unavailableNames.has(name))
    .flatMap((name) => snapshot.backendServices?.[name] || []);
  const unavailableBackendMetadata = unavailableNames.size
    ? snapshot.backendServiceMetadata.filter((backendService) => !knownBackendServices.includes(backendService))
    : [];
  command.backendServiceMetadata = unique([
    ...metadataValues(command.backendServiceMetadata),
    ...Object.keys(backendServices)
      .filter((name) => unavailableNames.has(name))
      .flatMap((name) => backendServices[name]),
    ...unavailableBackendMetadata,
  ]);

  const metadata = metadataSource(command);
  Object.keys(loadBalancerIndex).forEach((name) => {
    const oldReference = snapshot.loadBalancerReferences[name];
    if (oldReference) {
      metadata[oldReference.key] = metadata[oldReference.key].filter((value) => !oldReference.names.includes(value));
    }
  });
  (command.loadBalancers || []).forEach((loadBalancer: any) => {
    const name = loadBalancerName(loadBalancer);
    const refreshedReference = loadBalancerMetadataReference(name, loadBalancerIndex[name]);
    if (refreshedReference) {
      metadata[refreshedReference.key].push(...refreshedReference.names);
    }
  });
  unavailableNames.forEach((name) => {
    const reference = snapshot.loadBalancerReferences[name];
    if (reference) {
      metadata[reference.key].push(...reference.names);
    }
  });
  const attributedMetadata = Object.values(snapshot.loadBalancerReferences).reduce(
    (attributed, reference) => ({
      ...attributed,
      [reference.key]: unique([...(attributed[reference.key] || []), ...reference.names]),
    }),
    {} as Record<string, string[]>,
  );
  if (unavailableNames.size) {
    [GLOBAL_LOAD_BALANCER_NAMES, REGIONAL_LOAD_BALANCER_NAMES].forEach((key) => {
      metadata[key].push(
        ...snapshot.loadBalancerMetadata[key].filter((value) => !(attributedMetadata[key] || []).includes(value)),
      );
    });
  }
  command.loadBalancerMetadata = {
    [GLOBAL_LOAD_BALANCER_NAMES]: unique(metadata[GLOBAL_LOAD_BALANCER_NAMES]),
    [REGIONAL_LOAD_BALANCER_NAMES]: unique(metadata[REGIONAL_LOAD_BALANCER_NAMES]),
  };
  Object.keys(command.loadBalancerMetadata).forEach((key) => {
    if (!command.loadBalancerMetadata[key].length) {
      delete command.loadBalancerMetadata[key];
    }
  });
}

function indexedLoadBalancer(loadBalancerIndex: Record<string, any>, name: string): any {
  return (
    loadBalancerIndex[name] ||
    Object.values(loadBalancerIndex).find((loadBalancer: any) =>
      (loadBalancer.listeners || []).some((listener: any) => listener.name === name),
    )
  );
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean)));
}

function referenceValue(value: any, properties: string[]): string {
  if (typeof value === 'string') {
    return value;
  }
  return properties.map((property) => value?.[property]).find((property) => typeof property === 'string');
}

function referenceValues(values: any[], properties: string[]): string[] {
  return (values || []).map((value) => referenceValue(value, properties)).filter(Boolean);
}

function uniqueByReference(values: any[], properties: string[]): any[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const reference = referenceValue(value, properties);
    if (!reference || seen.has(reference)) {
      return false;
    }
    seen.add(reference);
    return true;
  });
}

function uniqueByName(values: any[]): any[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const name = loadBalancerName(value);
    if (!name || seen.has(name)) {
      return false;
    }
    seen.add(name);
    return true;
  });
}

function mergeRefreshedCommand(
  current: IGceServerGroupCommand,
  configured: IGceServerGroupCommand,
): IGceServerGroupCommand {
  const handlers = Object.keys(configured).reduce((result: Partial<IGceServerGroupCommand>, key) => {
    if (typeof configured[key] === 'function') {
      result[key] = configured[key];
    }
    return result;
  }, {});

  return {
    ...configured,
    ...current,
    ...handlers,
    backingData: configured.backingData,
    viewState: {
      ...configured.viewState,
      ...current.viewState,
      dirty: { ...configured.viewState?.dirty, ...current.viewState?.dirty },
    },
  };
}

function initializePipelineCreateCommand(
  baseCommand: IGceServerGroupCommand,
  placeholder: IGceServerGroupCommand,
): IGceServerGroupCommand {
  const placeholderViewState = placeholder.viewState || ({} as IGceServerGroupCommand['viewState']);
  return {
    ...baseCommand,
    ...(placeholderViewState.overrides || {}),
    viewState: {
      ...baseCommand.viewState,
      disableImageSelection: true,
      disableStrategySelection: placeholderViewState.disableStrategySelection || false,
      expectedArtifacts: placeholderViewState.expectedArtifacts || [],
      hideClusterNamePreview: placeholderViewState.hideClusterNamePreview || false,
      imageId: null,
      imageSourceText: placeholderViewState.imageSourceText,
      mode: 'createPipeline',
      pipeline: placeholderViewState.pipeline,
      readOnlyFields: placeholderViewState.readOnlyFields || {},
      showImageSourceSelector: true,
      stage: placeholderViewState.stage,
      submitButtonLabel: 'Add',
      templatingEnabled: true,
    },
  };
}
