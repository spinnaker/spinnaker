import { get, uniq } from 'lodash';
import React from 'react';

import type { Application, IModalComponentProps, ISubnet } from '@spinnaker/core';
import { AccountService, DeployInitializer, NameUtils, noop, ReactModal, REST } from '@spinnaker/core';

import { Container } from './container/Container';
import { EcsClusterReader } from '../../../ecsCluster/ecsCluster.read.service';
import { IamRoleReader } from '../../../iamRoles/iamRole.read.service';
import { MetricAlarmReader } from '../../../metricAlarm/metricAlarm.read.service';
import { EcsNetworking } from './networking/Networking';
import { SecretReader } from '../../../secrets/secret.read.service';
import type {
  IEcsCapacityProviderStrategyItem,
  IEcsDockerImage,
  IEcsServerGroupCommand,
  IEcsTargetGroupMapping,
} from '../serverGroupConfiguration.service';
import { ServiceDiscoveryReader } from '../../../serviceDiscovery/serviceDiscovery.read.service';
import { TaskDefinition } from './taskDefinition/TaskDefinition';

export interface IEcsCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: IEcsServerGroupCommand;
}

interface IEcsCloneServerGroupModalState {
  activeCapacityProviderIndex: number | null;
  command: IEcsServerGroupCommand;
  clusterQuery: string;
  launchTypeQuery: string;
  loaded: boolean;
  requiresTemplateSelection: boolean;
}

const TaskDefinitionReact = ({ children }: React.PropsWithChildren<object>) =>
  React.createElement('task-definition-react', null, children);

export class EcsCloneServerGroupModal extends React.Component<
  IEcsCloneServerGroupModalProps,
  IEcsCloneServerGroupModalState
> {
  private configureRequest = 0;
  private lastCapacityProviderMode: 'custom' | 'default' | null = null;
  private unmounted = false;

  public static defaultProps: Partial<IEcsCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private ecsClusterReader = new EcsClusterReader();
  private iamRoleReader = new IamRoleReader();
  private metricAlarmReader = new MetricAlarmReader();
  private secretReader = new SecretReader();

  public static show(props: IEcsCloneServerGroupModalProps): Promise<IEcsServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(EcsCloneServerGroupModal, props, modalProps);
  }

  constructor(props: IEcsCloneServerGroupModalProps) {
    super(props);
    this.ensureCommandShape(props.command);
    this.state = {
      activeCapacityProviderIndex: null,
      command: props.command,
      clusterQuery: props.command.ecsClusterName || '',
      launchTypeQuery: props.command.launchType || '',
      loaded: false,
      requiresTemplateSelection: get(props, 'command.viewState.requiresTemplateSelection', false),
    };
  }

  public componentDidMount(): void {
    if (this.state.requiresTemplateSelection) {
      this.setState({ loaded: true });
      return;
    }
    this.configureCommand();
  }

  public componentWillUnmount(): void {
    this.unmounted = true;
  }

  private templateSelected = () => {
    this.ensureCommandShape(this.props.command);
    this.lastCapacityProviderMode = null;
    this.setState(
      {
        activeCapacityProviderIndex: null,
        command: this.props.command,
        clusterQuery: this.props.command.ecsClusterName || '',
        launchTypeQuery: this.props.command.launchType || '',
        loaded: false,
        requiresTemplateSelection: false,
      },
      () => {
        this.configureCommand();
      },
    );
  };

  private configureCommand = (imageQuery = ''): PromiseLike<void> => {
    const command = this.state.command;
    const request = ++this.configureRequest;
    this.ensureCommandShape(command);
    return this.loadBackingData(command, imageQuery, request).then(() => {
      if (!this.unmounted && request === this.configureRequest) {
        this.setState({ command, loaded: true });
      }
    });
  };

  private safe<T>(promise: PromiseLike<T>, fallback: T): Promise<T> {
    return Promise.resolve(promise as Promise<T>).catch(() => fallback);
  }

  private loadBackingData(
    command: IEcsServerGroupCommand,
    imageQuery: string,
    request = this.configureRequest,
  ): Promise<void> {
    return Promise.all([
      this.safe(AccountService.getCredentialsKeyedByAccount('ecs'), {}),
      this.safe(REST('/loadBalancers').query({ provider: 'ecs' }).get(), []),
      this.safe(REST('/subnets/ecs').get(), []),
      this.safe(this.iamRoleReader.listRoles('ecs'), []),
      this.safe(this.ecsClusterReader.listClusters(), []),
      this.safe(this.ecsClusterReader.describeClusters(command.credentials, command.region), []),
      this.safe(this.metricAlarmReader.listMetricAlarms(), []),
      this.safe(REST('/securityGroups').get(), {}),
      this.safe(this.secretReader.listSecrets(), []),
      this.safe(ServiceDiscoveryReader.listServiceDiscoveryRegistries(), []),
      imageQuery
        ? this.safe(REST('/images/find').query({ provider: 'dockerRegistry', count: 50, q: imageQuery }).get(), [])
        : [],
    ]).then(
      ([
        credentialsKeyedByAccount,
        loadBalancers,
        subnets,
        iamRoles,
        ecsClusters,
        capacityProviderDetails,
        metricAlarms,
        securityGroups,
        secrets,
        serviceDiscoveryRegistries,
        images,
      ]) => {
        if (this.unmounted || request !== this.configureRequest) {
          return;
        }
        const loadBalancerList = Array.isArray(loadBalancers) ? loadBalancers : [];
        const subnetList = Array.isArray(subnets) ? subnets : [];
        const iamRoleList = Array.isArray(iamRoles) ? iamRoles : [];
        const clusterList = Array.isArray(ecsClusters) ? ecsClusters : [];
        const capacityProviderList = Array.isArray(capacityProviderDetails) ? capacityProviderDetails : [];
        const metricAlarmList = Array.isArray(metricAlarms) ? metricAlarms : [];
        const securityGroupData = securityGroups || {};
        const secretList = Array.isArray(secrets) ? secrets : [];
        const serviceDiscoveryRegistryList = Array.isArray(serviceDiscoveryRegistries)
          ? serviceDiscoveryRegistries
          : [];
        const imageList = Array.isArray(images) ? images : [];
        const backingData: any = command.backingData;
        backingData.credentialsKeyedByAccount = credentialsKeyedByAccount;
        backingData.loadBalancers = loadBalancerList;
        backingData.subnets = subnetList;
        backingData.iamRoles = iamRoleList;
        backingData.ecsClusters = clusterList;
        backingData.capacityProviderDetails = capacityProviderList;
        backingData.metricAlarms = metricAlarmList;
        backingData.securityGroups = securityGroupData;
        backingData.launchTypes = ['EC2', 'FARGATE'];
        backingData.networkModes = ['bridge', 'host', 'awsvpc', 'none', 'default'];
        backingData.secrets = secretList;
        backingData.serviceDiscoveryRegistries = serviceDiscoveryRegistryList;
        backingData.images = this.normalizeImages(command, imageList as IEcsDockerImage[]);
        backingData.filtered = {
          ...backingData.filtered,
          availableCapacityProviders: this.getAvailableCapacityProviders(command, capacityProviderList),
          defaultCapacityProviderStrategy: this.getDefaultCapacityProviderStrategy(command, capacityProviderList),
          ecsClusters: this.getClusterNames(command, clusterList),
          iamRoles: this.getIamRoles(command, iamRoleList),
          images: backingData.images,
          metricAlarms: metricAlarmList,
          securityGroupNames: this.getSecurityGroupNames(command, securityGroupData, subnetList),
          secrets: this.getSecrets(command, secretList),
          serviceDiscoveryRegistries: this.getServiceDiscoveryRegistries(command, serviceDiscoveryRegistryList),
          subnetTypes: this.getSubnetTypes(command, subnetList),
          targetGroups: this.getTargetGroups(command, loadBalancerList),
        };
        this.attachEventHandlers(command);
      },
    );
  }

  private ensureCommandShape(command: IEcsServerGroupCommand): void {
    command.backingData = command.backingData || ({} as any);
    command.backingData.filtered = command.backingData.filtered || ({} as any);
    command.backingData.filtered.availableCapacityProviders =
      command.backingData.filtered.availableCapacityProviders || [];
    command.backingData.filtered.defaultCapacityProviderStrategy =
      command.backingData.filtered.defaultCapacityProviderStrategy || [];
    command.backingData.filtered.ecsClusters = command.backingData.filtered.ecsClusters || [];
    command.backingData.filtered.iamRoles = command.backingData.filtered.iamRoles || [];
    command.backingData.filtered.images = command.backingData.filtered.images || [];
    command.backingData.filtered.metricAlarms = command.backingData.filtered.metricAlarms || [];
    command.backingData.filtered.securityGroupNames = command.backingData.filtered.securityGroupNames || [];
    command.backingData.filtered.secrets = command.backingData.filtered.secrets || [];
    command.backingData.filtered.serviceDiscoveryRegistries =
      command.backingData.filtered.serviceDiscoveryRegistries || [];
    command.backingData.filtered.subnetTypes = command.backingData.filtered.subnetTypes || [];
    command.backingData.filtered.targetGroups = command.backingData.filtered.targetGroups || [];
    command.backingData.iamRoles = command.backingData.iamRoles || [];
    command.backingData.launchTypes = command.backingData.launchTypes || ['EC2', 'FARGATE'];
    command.backingData.metricAlarms = command.backingData.metricAlarms || [];
    command.backingData.networkModes = command.backingData.networkModes || [
      'bridge',
      'host',
      'awsvpc',
      'none',
      'default',
    ];
    command.backingData.secrets = command.backingData.secrets || [];
    command.backingData.serviceDiscoveryRegistries = command.backingData.serviceDiscoveryRegistries || [];
    command.containerMappings = command.containerMappings || [];
    command.targetGroupMappings = command.targetGroupMappings || [];
    command.taskDefinitionArtifact = command.taskDefinitionArtifact || {};
    command.viewState = command.viewState || ({} as any);
    command.viewState.dirty = command.viewState.dirty || {};
    command.useTaskDefinitionArtifact = command.useTaskDefinitionArtifact === true;
  }

  private attachEventHandlers(command: IEcsServerGroupCommand): void {
    command.subnetTypeChanged = (changedCommand: IEcsServerGroupCommand = command) => {
      changedCommand.backingData.filtered.securityGroupNames = this.getSecurityGroupNames(
        changedCommand,
        changedCommand.backingData.securityGroups || {},
        changedCommand.backingData.subnets || [],
      );
      return { dirty: {} };
    };
    command.regionChanged = () => {
      this.configureCommand();
      return { dirty: {} };
    };
    command.credentialsChanged = () => {
      this.configureCommand();
      return { dirty: {} };
    };
    command.placementStrategyNameChanged = () => ({ dirty: {} });
    command.clusterChanged = () => {
      command.moniker = NameUtils.getMoniker(command.application, command.stack, command.freeFormDetails);
    };
  }

  private normalizeImages(command: IEcsServerGroupCommand, images: IEcsDockerImage[]): IEcsDockerImage[] {
    const commandImages = [
      ...(command.viewState.contextImages || []),
      command.imageDescription,
      ...command.containerMappings.map((mapping) => mapping.imageDescription),
    ].filter(Boolean);
    return uniq([...images, ...commandImages].map((image) => this.normalizeImage(image))).filter(Boolean);
  }

  private normalizeImage(image: any): IEcsDockerImage {
    const imageId = this.buildImageId(image);
    return {
      ...image,
      imageId,
      message: image.message || '',
      fromTrigger: !!image.fromTrigger,
      fromContext: !!image.fromContext,
      stageId: image.stageId || '',
      imageLabelOrSha: image.imageLabelOrSha || '',
    };
  }

  private buildImageId(image: any): string {
    if (image.imageId) {
      return image.imageId;
    }
    if (image.fromContext) {
      return image.imageLabelOrSha;
    }
    if (image.fromTrigger && !image.tag) {
      return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
    }
    if (image.registry && image.repository && image.tag) {
      return `${image.registry}/${image.repository}:${image.tag}`;
    }
    return image.reference || image.repository || '';
  }

  private getSubnetTypes(command: IEcsServerGroupCommand, subnets: ISubnet[]): ISubnet[] {
    return subnets.filter((subnet) => {
      const matchesAccount = !command.credentials || subnet.account === command.credentials;
      const matchesRegion = !command.region || subnet.region === command.region;
      return matchesAccount && matchesRegion && subnet.purpose;
    });
  }

  private getClusterNames(command: IEcsServerGroupCommand, clusters: any[]): string[] {
    return uniq(
      clusters
        .filter((cluster) => {
          const matchesAccount = !command.credentials || cluster.account === command.credentials;
          const matchesRegion = !command.region || cluster.region === command.region;
          return matchesAccount && matchesRegion;
        })
        .map((cluster) => cluster.name)
        .concat(command.ecsClusterName || []),
    ).filter(Boolean);
  }

  private getIamRoles(command: IEcsServerGroupCommand, iamRoles: any[]): string[] {
    const roleNames = iamRoles
      .filter((role) => role.accountName === command.credentials)
      .map((role) => role.name)
      .filter(Boolean);
    return roleNames.length ? ['None (No IAM role)', ...roleNames] : roleNames;
  }

  private getSecrets(command: IEcsServerGroupCommand, secrets: any[]): string[] {
    return secrets
      .filter((secret) => secret.account === command.credentials && secret.region === command.region)
      .map((secret) => secret.name)
      .filter(Boolean);
  }

  private getServiceDiscoveryRegistries(command: IEcsServerGroupCommand, registries: any[]): any[] {
    return registries
      .filter((registry) => registry.account === command.credentials && registry.region === command.region)
      .map((registry) => ({
        ...registry,
        displayName: registry.displayName || `${registry.name} (${registry.id})`,
      }));
  }

  private getSecurityGroupNames(command: IEcsServerGroupCommand, securityGroups: any, subnets: ISubnet[]): string[] {
    const subnetPurpose = command.subnetTypes?.[0] || command.subnetType;
    if (!subnetPurpose) {
      return [];
    }

    const subnet = subnets.find(
      (candidate) =>
        candidate.account === command.credentials &&
        candidate.region === command.region &&
        candidate.purpose === subnetPurpose,
    );
    const vpcId = subnet?.vpcId;
    if (!vpcId) {
      return [];
    }

    return (securityGroups?.[command.credentials]?.ecs?.[command.region] || [])
      .filter((securityGroup: any) => securityGroup.vpcId === vpcId)
      .map((securityGroup: any) => securityGroup.name)
      .filter(Boolean);
  }

  private getTargetGroups(command: IEcsServerGroupCommand, loadBalancers: any[]): string[] {
    const fromLoadBalancers = loadBalancers.flatMap((loadBalancer) =>
      (loadBalancer.accounts || []).flatMap((account: any) =>
        (account.regions || []).flatMap((region: any) =>
          (region.loadBalancers || []).flatMap((regionLoadBalancer: any) =>
            (regionLoadBalancer.targetGroups || []).map((targetGroup: any) =>
              typeof targetGroup === 'string' ? targetGroup : targetGroup.targetGroupName,
            ),
          ),
        ),
      ),
    );
    const fromCommand = (command.targetGroupMappings || []).map(
      (mapping: IEcsTargetGroupMapping) => mapping.targetGroup,
    );
    return uniq([...fromLoadBalancers, ...fromCommand, command.targetGroup]).filter(Boolean);
  }

  private getAvailableCapacityProviders(command: IEcsServerGroupCommand, capacityProviderDetails: any[]): string[] {
    return uniq(
      capacityProviderDetails
        .filter((details) => !command.ecsClusterName || details.clusterName === command.ecsClusterName)
        .flatMap((details) => details.capacityProviders || []),
    );
  }

  private getDefaultCapacityProviderStrategy(
    command: IEcsServerGroupCommand,
    capacityProviderDetails: any[],
  ): IEcsCapacityProviderStrategyItem[] {
    return capacityProviderDetails
      .filter((details) => !command.ecsClusterName || details.clusterName === command.ecsClusterName)
      .flatMap((details) => details.defaultCapacityProviderStrategy || []);
  }

  private updateCommand = (field: string, value: any) => {
    const command = this.state.command;
    command[field] = value;
    if (field === 'stack' || field === 'freeFormDetails' || field === 'ecsClusterName') {
      command.clusterChanged(command);
    }
    this.setState({ command }, () => {
      if (field === 'ecsClusterName') {
        this.configureCommand();
      }
    });
  };

  private selectCluster = (clusterName: string) => {
    this.setState({ clusterQuery: clusterName });
    this.updateCommand('ecsClusterName', clusterName);
  };

  private updateClusterQuery = (clusterQuery: string) => {
    const clusters = this.state.command.backingData.filtered.ecsClusters || [];
    const matchingCluster = clusters.find((cluster: string) => clusterQuery.endsWith(cluster));
    this.setState({ clusterQuery: matchingCluster || clusterQuery });
    if (matchingCluster) {
      this.updateCommand('ecsClusterName', matchingCluster);
    }
  };

  private selectLaunchType = (launchType: string) => {
    this.setState({ launchTypeQuery: launchType });
    this.updateCommand('launchType', launchType);
  };

  private updateLaunchTypeQuery = (launchTypeQuery: string) => {
    this.setState({ launchTypeQuery });
    if ((this.state.command.backingData.launchTypes || []).includes(launchTypeQuery)) {
      this.updateCommand('launchType', launchTypeQuery);
    }
  };

  private useDefaultCapacityProviders = () => {
    this.lastCapacityProviderMode = 'default';
    const defaultStrategy = this.state.command.backingData.filtered.defaultCapacityProviderStrategy?.[0];
    this.updateCommandFields({
      capacityProviderMode: 'default',
      capacityProviderStrategy: defaultStrategy ? [defaultStrategy] : [],
      useDefaultCapacityProviders: true,
    });
  };

  private useCustomCapacityProviders = () => {
    this.lastCapacityProviderMode = 'custom';
    this.updateCommandFields({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [],
      useDefaultCapacityProviders: false,
    });
  };

  private addCapacityProvider = () => {
    this.lastCapacityProviderMode = 'custom';
    this.updateCommandFields({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [
        ...(this.state.command.capacityProviderStrategy || []),
        { base: null, capacityProvider: '', weight: null },
      ],
      useDefaultCapacityProviders: false,
    });
  };

  private updateCapacityProvider = (
    index: number,
    field: keyof IEcsCapacityProviderStrategyItem,
    value: string | number,
  ) => {
    this.lastCapacityProviderMode = 'custom';
    const strategies = this.state.command.capacityProviderStrategy?.length
      ? [...this.state.command.capacityProviderStrategy]
      : [{ base: null, capacityProvider: '', weight: null }];
    const strategy = strategies[index] || { base: null, capacityProvider: '', weight: null };
    strategies[index] = { ...strategy, [field]: value };
    this.updateCommandFields({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: strategies,
      useDefaultCapacityProviders: false,
    });
  };

  private updateCommandFields = (fields: Partial<IEcsServerGroupCommand>) => {
    const command = this.state.command;
    Object.assign(command, fields);
    this.setState({ command });
  };

  private notifyAngular = (field: string, value: any) => {
    const currentCommand = this.state.command;
    const command =
      field === 'pipeline'
        ? { ...currentCommand, viewState: { ...currentCommand.viewState, pipeline: value } }
        : { ...currentCommand, [field]: value };
    if (field === 'pipeline') {
      currentCommand.viewState = command.viewState;
    } else {
      currentCommand[field] = value;
    }
    this.setState({ command });
  };

  private submit = () => {
    this.props.closeModal(this.state.command);
  };

  public render() {
    const { application, dismissModal, title } = this.props;
    const { clusterQuery, command, launchTypeQuery, loaded, requiresTemplateSelection } = this.state;

    if (requiresTemplateSelection) {
      return (
        <DeployInitializer
          application={application}
          cloudProvider="ecs"
          command={command as any}
          onDismiss={dismissModal}
          onTemplateSelected={this.templateSelected}
        />
      );
    }

    const clusterOptions = (command.backingData.filtered.ecsClusters || []).map((cluster) => ({
      label: cluster,
      value: cluster,
    }));
    const launchTypeOptions = (command.backingData.launchTypes || []).map((launchType) => ({
      label: launchType,
      value: launchType,
    }));
    const capacityProviderStrategy = command.capacityProviderStrategy || [];
    const showingCustomCapacityProviders =
      command.useDefaultCapacityProviders === false ||
      command.capacityProviderMode === 'custom' ||
      this.lastCapacityProviderMode === 'custom';
    const displayedCapacityProviderStrategy = capacityProviderStrategy;

    return (
      <div className="modal-content">
        <div className="modal-header">
          <button type="button" className="close" onClick={dismissModal}>
            <span>&times;</span>
          </button>
          <h3 className="modal-title">{title}</h3>
        </div>
        <div className="modal-body form-horizontal">
          {!loaded ? (
            <div className="load medium">
              <div className="message">Loading server group configuration...</div>
              <div className="bars">
                <div className="bar" />
                <div className="bar" />
                <div className="bar" />
              </div>
            </div>
          ) : (
            <>
              <div className="form-group">
                <label className="col-md-3 sm-label-right">Cluster</label>
                <div className="col-md-7" data-test-id="ServerGroup.clusterName">
                  <input
                    className="form-control input-sm"
                    value={clusterQuery}
                    onFocus={() => this.setState({ clusterQuery: '' })}
                    onChange={(event) => this.updateClusterQuery(event.target.value)}
                  />
                  {clusterOptions.map((cluster) => (
                    <button
                      className="Select-option"
                      key={cluster.value}
                      onClick={() => this.selectCluster(cluster.value)}
                      style={{ display: 'block' }}
                      type="button"
                    >
                      <span>{cluster.label}</span>
                    </button>
                  ))}
                </div>
              </div>
              <div className="form-group">
                <label className="col-md-3 sm-label-right">Stack</label>
                <div className="col-md-7">
                  <input
                    className="form-control input-sm"
                    data-test-id="ServerGroup.stack"
                    value={command.stack || ''}
                    onChange={(event) => this.updateCommand('stack', event.target.value)}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="col-md-3 sm-label-right">Details</label>
                <div className="col-md-7">
                  <input
                    className="form-control input-sm"
                    data-test-id="ServerGroup.details"
                    value={command.freeFormDetails || ''}
                    onChange={(event) => this.updateCommand('freeFormDetails', event.target.value)}
                  />
                </div>
              </div>

              <EcsNetworking
                command={command}
                notifyAngular={this.notifyAngular}
                configureCommand={this.configureCommand}
              />

              <div className="form-group">
                <label className="col-md-3 sm-label-right">Task Definition</label>
                <div className="col-md-7">
                  <button
                    type="button"
                    className="btn btn-default"
                    data-test-id="ServerGroup.useArtifacts"
                    onClick={() => this.updateCommand('useTaskDefinitionArtifact', true)}
                  >
                    Use artifacts
                  </button>
                  <button
                    type="button"
                    className="btn btn-default"
                    data-test-id="ServerGroup.useInputs"
                    onClick={() => this.updateCommand('useTaskDefinitionArtifact', false)}
                  >
                    Use container inputs
                  </button>
                </div>
              </div>

              {command.useTaskDefinitionArtifact ? (
                <TaskDefinitionReact>
                  <TaskDefinition
                    command={command}
                    notifyAngular={this.notifyAngular}
                    configureCommand={this.configureCommand}
                  />
                </TaskDefinitionReact>
              ) : (
                <Container
                  command={command}
                  notifyAngular={this.notifyAngular}
                  configureCommand={this.configureCommand}
                />
              )}

              <div className="form-group">
                <label className="col-md-3 sm-label-right">Launch Type</label>
                <div className="col-md-7">
                  <input
                    className="form-control input-sm"
                    data-test-id="ServerGroup.launchType"
                    value={launchTypeQuery}
                    onFocus={() => this.setState({ launchTypeQuery: '' })}
                    onChange={(event) => this.updateLaunchTypeQuery(event.target.value)}
                  />
                  {launchTypeOptions.map((launchType) => (
                    <span
                      className="ui-select-highlight"
                      key={launchType.value}
                      onClick={() => this.selectLaunchType(launchType.value)}
                      style={{ display: 'block' }}
                    >
                      {launchType.label}
                    </span>
                  ))}
                </div>
              </div>
              <div className="form-group">
                <label className="col-md-3 sm-label-right">Logging</label>
                <div className="col-md-7">
                  <input
                    className="form-control input-sm"
                    data-test-id="Logging.logDriver"
                    value={command.logDriver || ''}
                    onChange={(event) => this.updateCommand('logDriver', event.target.value)}
                  />
                  {command.logDriver && <span>{command.logDriver}</span>}
                </div>
              </div>
              <div className="form-group">
                <label className="col-md-3 sm-label-right">Capacity Providers</label>
                <div className="col-md-7">
                  <button
                    type="button"
                    className="btn btn-default"
                    data-test-id="ServerGroup.computeOptionsCapacityProviders"
                  >
                    Capacity providers
                  </button>
                  <div className="radio">
                    <label>
                      <input
                        data-test-id="ServerGroup.capacityProviders.default"
                        type="radio"
                        checked={command.useDefaultCapacityProviders === true}
                        onClick={this.useDefaultCapacityProviders}
                        onChange={noop}
                      />
                      Use cluster default
                    </label>
                  </div>
                  <div className="radio">
                    <label>
                      <input
                        data-test-id="ServerGroup.capacityProviders.custom"
                        type="radio"
                        checked={command.useDefaultCapacityProviders === false}
                        onClick={this.useCustomCapacityProviders}
                        onChange={noop}
                      />
                      Use custom
                    </label>
                  </div>
                  {!showingCustomCapacityProviders && (
                    <>
                      <input
                        className="form-control input-sm"
                        data-test-id="ServerGroup.defaultCapacityProvider.name.0"
                        disabled={true}
                        value={command.capacityProviderStrategy?.[0]?.capacityProvider || ''}
                      />
                      <input
                        className="form-control input-sm"
                        data-test-id="ServerGroup.capacityProvider.base.0"
                        disabled={true}
                        value={command.capacityProviderStrategy?.[0]?.base ?? ''}
                      />
                      <input
                        className="form-control input-sm"
                        data-test-id="ServerGroup.capacityProvider.weight.0"
                        disabled={true}
                        value={command.capacityProviderStrategy?.[0]?.weight ?? ''}
                      />
                    </>
                  )}
                  {showingCustomCapacityProviders && (
                    <>
                      <button
                        type="button"
                        className="btn btn-block btn-sm add-new"
                        data-test-id="ServerGroup.addCapacityProvider"
                        onClick={this.addCapacityProvider}
                      >
                        Add New Capacity Provider
                      </button>
                      {displayedCapacityProviderStrategy.map((strategy, index) => (
                        <React.Fragment key={index}>
                          <input
                            className="form-control input-sm"
                            data-test-id={`ServerGroup.customCapacityProvider.name.${index}`}
                            onFocus={() => this.setState({ activeCapacityProviderIndex: index })}
                            onChange={(event) => {
                              this.setState({ activeCapacityProviderIndex: index });
                              this.updateCapacityProvider(index, 'capacityProvider', event.target.value);
                            }}
                            value={strategy.capacityProvider || ''}
                          />
                          {this.state.activeCapacityProviderIndex === index && (
                            <div
                              className="Select-option"
                              onClick={() => {
                                this.updateCapacityProvider(index, 'capacityProvider', 'FARGATE_SPOT');
                                this.setState({ activeCapacityProviderIndex: null });
                              }}
                            >
                              FARGATE_SPOT
                            </div>
                          )}
                          <input
                            className="form-control input-sm"
                            data-test-id={`ServerGroup.capacityProvider.base.${index}`}
                            value={strategy.base ?? ''}
                            onChange={(event) => this.updateCapacityProvider(index, 'base', Number(event.target.value))}
                          />
                          <input
                            className="form-control input-sm"
                            data-test-id={`ServerGroup.capacityProvider.weight.${index}`}
                            value={strategy.weight ?? ''}
                            onChange={(event) =>
                              this.updateCapacityProvider(index, 'weight', Number(event.target.value))
                            }
                          />
                        </React.Fragment>
                      ))}
                    </>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-default" onClick={dismissModal}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-primary"
            data-test-id="ServerGroupWizard.submitButton"
            onClick={this.submit}
          >
            {command.viewState?.submitButtonLabel || 'Done'}
          </button>
        </div>
      </div>
    );
  }
}
