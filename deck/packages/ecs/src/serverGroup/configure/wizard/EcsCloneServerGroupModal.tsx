import { get, uniq, uniqBy } from 'lodash';
import React from 'react';

import type {
  Application,
  DeckRuntimeServices,
  IModalComponentProps,
  IRouterInjectedProps,
  ISubnet,
  IWizardPageInjectedProps,
} from '@spinnaker/core';
import {
  AccountService,
  DeckRuntimeContext,
  DeployInitializer,
  NameUtils,
  noop,
  ReactModal,
  REST,
  TaskMonitor,
  withRouter,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { EcsClusterReader } from '../../../ecsCluster/ecsCluster.read.service';
import { IamRoleReader } from '../../../iamRoles/iamRole.read.service';
import { MetricAlarmReader } from '../../../metricAlarm/metricAlarm.read.service';
import { AdvancedSettings } from './pages/AdvancedSettings';
import { BasicSettings } from './pages/BasicSettings';
import { ContainerSettings } from './pages/ContainerSettings';
import { HorizontalScalingSettings } from './pages/HorizontalScalingSettings';
import { LoggingSettings } from './pages/LoggingSettings';
import { NetworkingSettings } from './pages/NetworkingSettings';
import { ServiceDiscoverySettings } from './pages/ServiceDiscoverySettings';
import { TaskDefinitionSettings } from './pages/TaskDefinitionSettings';
import {
  EcsWizardPageValidation,
  validateEcsBasicSettings,
  validateEcsCapacity,
  validateEcsContainer,
  validateEcsServerGroup,
  validateEcsServiceDiscovery,
  validateEcsTaskDefinition,
} from './pages/validation';
import { PlacementStrategyService } from '../../../placementStrategy/placementStrategy.service';
import { SecretReader } from '../../../secrets/secret.read.service';
import type {
  IEcsCapacityProviderStrategyItem,
  IEcsDockerImage,
  IEcsServerGroupCommand,
  IEcsTargetGroupMapping,
} from '../serverGroupConfiguration.service';
import { ServiceDiscoveryReader } from '../../../serviceDiscovery/serviceDiscovery.read.service';

type EcsFormikProps = IWizardPageInjectedProps<IEcsServerGroupCommand>['formik'];

export interface IEcsCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: IEcsServerGroupCommand;
}

interface IEcsCloneServerGroupModalState {
  command: IEcsServerGroupCommand;
  loaded: boolean;
  requiresTemplateSelection: boolean;
  taskMonitor: TaskMonitor;
}

export class EcsCloneServerGroupModalComponent extends React.Component<
  IEcsCloneServerGroupModalProps & IRouterInjectedProps,
  IEcsCloneServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private command: IEcsServerGroupCommand;
  private configureRequest = 0;
  private formik: EcsFormikProps = null;
  private unmounted = false;

  public static defaultProps: Partial<IEcsCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private ecsClusterReader = new EcsClusterReader();
  private iamRoleReader = new IamRoleReader();
  private metricAlarmReader = new MetricAlarmReader();
  private placementStrategyService = new PlacementStrategyService();
  private secretReader = new SecretReader();

  public static show(
    props: IEcsCloneServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IEcsServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(EcsCloneServerGroupModal, props, modalProps, runtimeServices);
  }

  constructor(props: IEcsCloneServerGroupModalProps & IRouterInjectedProps) {
    super(props);
    this.ensureCommandShape(props.command);
    this.command = props.command;
    this.state = {
      command: props.command,
      loaded: false,
      requiresTemplateSelection: get(props, 'command.viewState.requiresTemplateSelection', false),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
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

  private onTaskComplete = () => {
    this.props.application.serverGroups.refresh();
    this.props.application.serverGroups.onNextRefresh(null, this.onApplicationRefresh);
  };

  private onApplicationRefresh = (): void => {
    if (this.unmounted) {
      return;
    }

    const { command, taskMonitor } = this.state;
    const cloneStage = taskMonitor.task?.execution?.stages?.find((stage: any) => stage.type === 'cloneServerGroup');
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
      provider: 'ecs',
      region: command.region,
      serverGroup: newServerGroupName,
    });
  };

  private templateSelected = () => {
    this.ensureCommandShape(this.props.command);
    this.setState(
      {
        command: this.props.command,
        loaded: false,
        requiresTemplateSelection: false,
      },
      () => {
        this.configureCommand();
      },
    );
  };

  private configureCommand = (imageQuery = '', command = this.state.command): PromiseLike<void> => {
    const request = ++this.configureRequest;
    this.ensureCommandShape(command);
    this.command = command;
    return this.loadBackingData(command, imageQuery, request).then(() => {
      if (!this.unmounted && request === this.configureRequest) {
        const configuredCommand = this.command;
        configuredCommand.availabilityZones = command.availabilityZones;
        configuredCommand.backingData = command.backingData;
        configuredCommand.region = command.region;
        this.attachEventHandlers(configuredCommand);
        this.syncCommand(configuredCommand);
        this.setState({ loaded: true });
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
        : command.backingData.images || [],
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
        this.reconcileLocation(command);
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
    command.serviceDiscoveryAssociations = command.serviceDiscoveryAssociations || [];
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
    command.regionChanged = (changedCommand: IEcsServerGroupCommand = command) => {
      this.reconcileLocation(changedCommand);
      this.syncCommand(changedCommand);
      this.configureCommand('', changedCommand);
      return { dirty: {} };
    };
    command.credentialsChanged = (changedCommand: IEcsServerGroupCommand = command) => {
      this.reconcileLocation(changedCommand);
      this.syncCommand(changedCommand);
      this.configureCommand('', changedCommand);
      return { dirty: {} };
    };
    command.placementStrategyNameChanged = (changedCommand: IEcsServerGroupCommand = command) => {
      changedCommand.placementStrategySequence = this.placementStrategyService.getPredefinedStrategy(
        changedCommand.placementStrategyName,
      );
      return { dirty: {} };
    };
    command.clusterChanged = () => {
      command.moniker = NameUtils.getMoniker(command.application, command.stack, command.freeFormDetails);
    };
  }

  private reconcileLocation(command: IEcsServerGroupCommand): void {
    const account = command.backingData.credentialsKeyedByAccount?.[command.credentials];
    const regions = account?.regions || [];
    command.backingData.filtered.regions = regions;

    const selectedRegion = regions.find((region: any) => region.name === command.region);
    if (!selectedRegion) {
      command.region = null;
    }
    const availabilityZones = selectedRegion?.availabilityZones || [];
    command.backingData.filtered.availabilityZones = availabilityZones;
    command.availabilityZones = availabilityZones;
  }

  private normalizeImages(command: IEcsServerGroupCommand, images: IEcsDockerImage[]): IEcsDockerImage[] {
    const commandImages = [
      ...(command.viewState.contextImages || []),
      command.imageDescription,
      ...command.containerMappings.map((mapping) => mapping.imageDescription),
    ].filter(Boolean);
    return uniqBy(
      [...images, ...commandImages].map((image) => this.normalizeImage(image)),
      (image) => image.imageId || image.id || image.name,
    ).filter(Boolean);
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
      (loadBalancer.accounts || [])
        .filter((account: any) => account.name === command.credentials)
        .flatMap((account: any) =>
          (account.regions || [])
            .filter((region: any) => region.name === command.region)
            .flatMap((region: any) =>
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

  private syncCommand(command: IEcsServerGroupCommand, formik = this.formik): void {
    this.command = command;
    formik?.setValues(command);
    this.setState({ command });
  }

  private updateCommand = (formik: EcsFormikProps, field: string, value: any) => {
    if (field === 'credentials' || field === 'region' || field === 'ecsClusterName' || field === 'subnetTypes') {
      this.configureRequest += 1;
    }
    formik.setFieldValue(field, value);
    const command = { ...this.command, [field]: value };
    if (field === 'stack' || field === 'freeFormDetails') {
      command.clusterChanged(command);
    }
    if (field === 'credentials') {
      command.credentialsChanged(command);
    }
    if (field === 'region') {
      command.regionChanged(command);
    }
    if (field === 'subnetTypes') {
      command.subnetTypeChanged(command);
    }
    if (field === 'placementStrategyName') {
      command.placementStrategyNameChanged(command);
    }
    if (field === 'useTaskDefinitionArtifact') {
      command.serviceDiscoveryAssociations = (command.serviceDiscoveryAssociations || []).map((association) => ({
        ...association,
        containerName: value ? association.containerName || '' : null,
      }));
    }
    if (field !== 'credentials' && field !== 'region') {
      this.syncCommand(command, formik);
    }
    this.setState({ command }, () => {
      if (field === 'ecsClusterName' || field === 'subnetTypes') {
        this.configureCommand('', command);
      }
    });
  };

  private submit = (command: IEcsServerGroupCommand = this.state.command) => {
    const { taskMonitor } = this.state;
    this.setState({ command });
    const mode = command.viewState?.mode;
    if (mode === 'editPipeline' || mode === 'createPipeline') {
      return this.props.closeModal(command);
    }
    return taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.cloneServerGroup(command as any, this.props.application),
    );
  };

  public render() {
    const { application, dismissModal, title } = this.props;
    const { command, loaded, requiresTemplateSelection } = this.state;

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

    return (
      <WizardModal<IEcsServerGroupCommand>
        closeModal={this.submit}
        dismissModal={dismissModal}
        heading={title}
        initialValues={command}
        loading={!loaded}
        submitButtonLabel={command.viewState?.submitButtonLabel || 'Done'}
        taskMonitor={this.state.taskMonitor}
        validate={validateEcsServerGroup}
        render={({ formik, nextIdx, wizard }) => {
          this.formik = formik;
          const configureFormikCommand = (query = '') => this.configureCommand(query, formik.values);
          const updateFormikCommand = (field: string, value: any) => this.updateCommand(formik, field, value);
          return (
            <>
              <WizardPage
                label="Basic Settings"
                order={nextIdx()}
                render={({ innerRef }) => (
                  <EcsWizardPageValidation ref={innerRef} validator={validateEcsBasicSettings}>
                    <BasicSettings
                      application={application}
                      command={formik.values}
                      configureCommand={configureFormikCommand}
                      onFieldChange={updateFormikCommand}
                    />
                  </EcsWizardPageValidation>
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Networking"
                order={nextIdx()}
                render={() => (
                  <NetworkingSettings
                    application={application}
                    command={formik.values}
                    configureCommand={configureFormikCommand}
                    onFieldChange={updateFormikCommand}
                  />
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Task Definition"
                order={nextIdx()}
                render={({ innerRef }) => (
                  <EcsWizardPageValidation ref={innerRef} validator={validateEcsTaskDefinition}>
                    <TaskDefinitionSettings
                      application={application}
                      command={formik.values}
                      configureCommand={configureFormikCommand}
                      onFieldChange={updateFormikCommand}
                    />
                  </EcsWizardPageValidation>
                )}
                wizard={wizard}
              />
              {!formik.values.useTaskDefinitionArtifact && (
                <WizardPage
                  label="Container"
                  order={nextIdx()}
                  render={({ innerRef }) => (
                    <EcsWizardPageValidation ref={innerRef} validator={validateEcsContainer}>
                      <ContainerSettings
                        application={application}
                        command={formik.values}
                        configureCommand={configureFormikCommand}
                        onFieldChange={updateFormikCommand}
                      />
                    </EcsWizardPageValidation>
                  )}
                  wizard={wizard}
                />
              )}
              <WizardPage
                label="Horizontal Scaling"
                order={nextIdx()}
                render={({ innerRef }) => (
                  <EcsWizardPageValidation ref={innerRef} validator={validateEcsCapacity}>
                    <HorizontalScalingSettings
                      application={application}
                      command={formik.values}
                      configureCommand={configureFormikCommand}
                      onFieldChange={updateFormikCommand}
                    />
                  </EcsWizardPageValidation>
                )}
                wizard={wizard}
              />
              {!formik.values.useTaskDefinitionArtifact && (
                <WizardPage
                  label="Logging"
                  order={nextIdx()}
                  render={() => (
                    <LoggingSettings
                      application={application}
                      command={formik.values}
                      configureCommand={configureFormikCommand}
                      onFieldChange={updateFormikCommand}
                    />
                  )}
                  wizard={wizard}
                />
              )}
              <WizardPage
                label="Service Discovery"
                order={nextIdx()}
                render={({ innerRef }) => (
                  <EcsWizardPageValidation ref={innerRef} validator={validateEcsServiceDiscovery}>
                    <ServiceDiscoverySettings
                      application={application}
                      command={formik.values}
                      configureCommand={configureFormikCommand}
                      onFieldChange={updateFormikCommand}
                    />
                  </EcsWizardPageValidation>
                )}
                wizard={wizard}
              />
              <WizardPage
                label="Advanced Settings"
                order={nextIdx()}
                render={() => (
                  <AdvancedSettings
                    application={application}
                    command={formik.values}
                    configureCommand={configureFormikCommand}
                    onFieldChange={updateFormikCommand}
                  />
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

export const EcsCloneServerGroupModal = Object.assign(
  withRouter<IEcsCloneServerGroupModalProps & IRouterInjectedProps>(EcsCloneServerGroupModalComponent),
  { show: EcsCloneServerGroupModalComponent.show },
);
