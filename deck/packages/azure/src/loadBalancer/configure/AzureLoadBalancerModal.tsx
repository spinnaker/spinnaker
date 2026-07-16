import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import {
  AccountService,
  LoadBalancerWriter,
  ModalClose,
  NameUtils,
  NetworkReader,
  noop,
  ReactInjector,
  ReactModal,
  SubmitButton,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import { AzureLoadBalancerTransformer } from '../loadBalancer.transformer';
import type { IAzureLoadBalancer } from '../../utility';
import { AzureLoadBalancerTypes } from '../../utility';

interface IAzureLoadBalancerModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: any;
  loadBalancerType?: IAzureLoadBalancer | string;
}

interface IAzureVnet {
  account: string;
  name: string;
  region: string;
  resourceGroup?: string;
  subnets?: IAzureSubnet[];
}

interface IAzureSubnet {
  devices?: Array<{ type: string }>;
  name: string;
}

interface IAzureLoadBalancerModalState {
  accounts: any[];
  accountsLoaded: boolean;
  existingLoadBalancerNames: string[];
  loadBalancer: any;
  regions: any[];
  selectedSubnets: IAzureSubnet[];
  selectedVnets: IAzureVnet[];
  taskMonitor: TaskMonitor;
}

interface IValidationOptions {
  existingLoadBalancerNames?: string[];
  isNew?: boolean;
}

export const validSkus = ['Standard_v2', 'Standard_Small'];

function normalizeLoadBalancerTypeName(loadBalancerType: any): string {
  const normalized = String(loadBalancerType || '')
    .toLowerCase()
    .split('_')
    .join(' ');
  if (normalized === 'application gateway') {
    return 'azure application gateway';
  }
  if (normalized === 'load balancer') {
    return 'azure load balancer';
  }
  return normalized;
}

export function getAzureLoadBalancerTypeChoice(
  loadBalancer?: any,
  loadBalancerType?: IAzureLoadBalancer | string,
): IAzureLoadBalancer {
  const explicitType = typeof loadBalancerType === 'string' ? loadBalancerType : loadBalancerType?.type;
  const normalizedType = normalizeLoadBalancerTypeName(
    explicitType || loadBalancer?.loadBalancerType || loadBalancer?.elb?.loadBalancerType,
  );
  return (
    AzureLoadBalancerTypes.find((candidate) => candidate.type.toLowerCase() === normalizedType) ||
    AzureLoadBalancerTypes[0]
  );
}

export function formatInputValue(value: any): any {
  return value ?? '';
}

export function getAzureVnetSelectValue(vnet: IAzureVnet): string {
  return `${vnet.name}|${vnet.resourceGroup || ''}`;
}

export function getAzureVnetSelectLabel(vnet: IAzureVnet): string {
  return vnet.resourceGroup ? `${vnet.name} (${vnet.resourceGroup})` : vnet.name;
}

export function findAzureVnetBySelectValue(vnets: IAzureVnet[], value: string): IAzureVnet | null {
  return vnets.find((vnet) => getAzureVnetSelectValue(vnet) === value) || null;
}

export function liftAzureLoadBalancerSessionPersistence(
  loadBalancer: any,
  loadBalancerType: string,
  sourceLoadBalancer = loadBalancer,
): any {
  if (!isAzureLoadBalancerType(loadBalancerType)) {
    return loadBalancer;
  }

  const sourceRule =
    sourceLoadBalancer.elb?.loadBalancingRules?.[0] || sourceLoadBalancer.loadBalancingRules?.[0] || {};
  const convertedRule = loadBalancer.loadBalancingRules?.[0] || {};

  return {
    ...loadBalancer,
    sessionPersistence:
      loadBalancer.sessionPersistence ||
      sourceLoadBalancer.elb?.sessionPersistence ||
      sourceRule.persistence ||
      sourceRule.sessionPersistence ||
      convertedRule.persistence ||
      convertedRule.sessionPersistence,
  };
}

export function isAzureLoadBalancerType(loadBalancerType: string): boolean {
  return loadBalancerType === 'Azure Load Balancer';
}

export function shouldRenderCreateFields(isNew: boolean): boolean {
  return isNew;
}

export function shouldRenderNetworkFields(isNew: boolean, loadBalancerType: string): boolean {
  return isNew && !isAzureLoadBalancerType(loadBalancerType);
}

export function shouldRenderSkuField(isNew: boolean, loadBalancerType: string): boolean {
  return isNew && !isAzureLoadBalancerType(loadBalancerType);
}

export function listenerProtocolOptions(loadBalancerType: string): string[] {
  return isAzureLoadBalancerType(loadBalancerType) ? ['TCP', 'UDP'] : ['HTTP'];
}

export function probeProtocolOptions(loadBalancerType: string): string[] {
  return isAzureLoadBalancerType(loadBalancerType) ? ['TCP', 'HTTP'] : ['HTTP'];
}

export function parseOptionalNumber(value: any): number {
  if (value === '' || value === null || value === undefined) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function applyAzureLoadBalancerTypeDefaults(loadBalancer: any, loadBalancerType: string): any {
  const listenerOptions = listenerProtocolOptions(loadBalancerType);
  const probeOptions = probeProtocolOptions(loadBalancerType);
  return {
    ...loadBalancer,
    sessionPersistence: isAzureLoadBalancerType(loadBalancerType)
      ? loadBalancer.sessionPersistence || 'None'
      : loadBalancer.sessionPersistence,
    probes: (loadBalancer.probes || [{}]).map((probe: any) => ({
      ...probe,
      probeProtocol: probeOptions.includes(probe.probeProtocol) ? probe.probeProtocol : probeOptions[0],
    })),
    loadBalancingRules: (loadBalancer.loadBalancingRules || [{}]).map((rule: any) => ({
      ...rule,
      protocol: listenerOptions.includes(rule.protocol) ? rule.protocol : listenerOptions[0],
    })),
  };
}

function isPresent(value: any): boolean {
  return value !== '' && value !== null && value !== undefined;
}

function isNegativeNumber(value: any): boolean {
  if (!isPresent(value)) {
    return false;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed < 0;
}

function validateNonNegative(errors: string[], value: any, message: string): void {
  if (isNegativeNumber(value)) {
    errors.push(message);
  }
}

function isValidStack(value: string): boolean {
  return /^[a-zA-Z0-9]+$/.test(value || '');
}

function isValidDetail(value: string): boolean {
  return /^[a-zA-Z0-9-]+$/.test(value || '');
}

export function validateAzureLoadBalancerForSubmit(
  loadBalancer: any,
  loadBalancerType: string,
  options: IValidationOptions = {},
): string[] {
  const errors: string[] = [];
  const probe = loadBalancer.probes?.[0] || {};
  const rules = loadBalancer.loadBalancingRules || [];

  if (!loadBalancer.name) {
    errors.push('Name is required.');
  } else if (loadBalancer.name.length > 32) {
    errors.push('Name must be 32 characters or fewer.');
  }

  if (options.isNew) {
    if (!loadBalancer.credentials) {
      errors.push('Account is required.');
    }
    if (!loadBalancer.region) {
      errors.push('Region is required.');
    }
    if (!loadBalancer.stack) {
      errors.push('Stack is required.');
    } else if (!isValidStack(loadBalancer.stack)) {
      errors.push('Stack may only contain letters and numbers.');
    }
    if (!loadBalancer.detail) {
      errors.push('Detail is required.');
    } else if (!isValidDetail(loadBalancer.detail)) {
      errors.push('Detail may only contain letters, numbers, and hyphens.');
    }
    if (
      (options.existingLoadBalancerNames || []).some((name) => name.toLowerCase() === loadBalancer.name.toLowerCase())
    ) {
      errors.push(`A load balancer named ${loadBalancer.name} already exists in this account and region.`);
    }
    if (!isAzureLoadBalancerType(loadBalancerType)) {
      if (!loadBalancer.selectedVnet && !loadBalancer.vnet) {
        errors.push('VNet is required.');
      }
      if (!loadBalancer.selectedSubnet && !loadBalancer.subnet) {
        errors.push('Subnet is required.');
      }
    }
  }

  if (!rules.length) {
    errors.push('At least one listener is required.');
  }

  rules.forEach((rule: any, index: number) => {
    if (!listenerProtocolOptions(loadBalancerType).includes(rule.protocol)) {
      errors.push(`Listener ${index + 1} protocol is invalid.`);
    }
    if (!isPresent(rule.externalPort)) {
      errors.push(`Listener ${index + 1} external port is required.`);
    } else {
      validateNonNegative(errors, rule.externalPort, `Listener ${index + 1} external port must be 0 or greater.`);
    }
    if (!isPresent(rule.backendPort)) {
      errors.push(`Listener ${index + 1} backend port is required.`);
    } else {
      validateNonNegative(errors, rule.backendPort, `Listener ${index + 1} backend port must be 0 or greater.`);
    }
    validateNonNegative(errors, rule.idleTimeout, `Listener ${index + 1} idle timeout must be 0 or greater.`);
  });

  if (!probeProtocolOptions(loadBalancerType).includes(probe.probeProtocol)) {
    errors.push('Health check protocol is invalid.');
  }
  if (!isPresent(probe.probePort)) {
    errors.push('Health check port is required.');
  } else {
    validateNonNegative(errors, probe.probePort, 'Health check port must be 0 or greater.');
  }
  if (probe.probeProtocol?.indexOf('HTTP') === 0 && !probe.probePath) {
    errors.push('Health check path is required.');
  }
  validateNonNegative(errors, probe.probeInterval, 'Health check interval must be 0 or greater.');
  validateNonNegative(errors, probe.unhealthyThreshold, 'Unhealthy threshold must be 0 or greater.');
  validateNonNegative(errors, probe.timeout, 'Health check timeout must be 0 or greater.');

  return errors;
}

export function normalizeAzureLoadBalancerForSubmit(loadBalancer: any, loadBalancerType: string): any {
  const normalized = {
    ...loadBalancer,
    probes: (loadBalancer.probes || []).map((probe: any) => ({ ...probe })),
    loadBalancingRules: (loadBalancer.loadBalancingRules || []).map((rule: any) => ({ ...rule })),
  };

  if (!isAzureLoadBalancerType(loadBalancerType) && normalized.selectedVnet) {
    normalized.vnet = normalized.selectedVnet.name;
    normalized.vnetResourceGroup = normalized.selectedVnet.resourceGroup;
  }

  if (!isAzureLoadBalancerType(loadBalancerType) && normalized.selectedSubnet) {
    normalized.subnet = normalized.selectedSubnet.name;
  }

  const name = normalized.clusterName || normalized.name;
  const probeName = `${name}-probe`;
  const ruleNameBase = `${name}-rule`;
  normalized.type = 'upsertLoadBalancer';
  normalized.loadBalancerType = loadBalancerType;

  if (!normalized.vnet && !normalized.subnetType) {
    normalized.securityGroups = null;
  }

  if (normalized.probes[0]) {
    normalized.probes[0].probeName = probeName;
    if (normalized.probes[0].probeProtocol === 'TCP') {
      normalized.probes[0].probePath = undefined;
    }
  }

  normalized.loadBalancingRules.forEach((rule: any, index: number) => {
    delete rule.persistence;
    delete rule.sessionPersistence;
    rule.ruleName = `${ruleNameBase}${index}`;
    rule.probeName = probeName;
  });

  return normalized;
}

export class AzureLoadBalancerModal extends React.Component<
  IAzureLoadBalancerModalProps,
  IAzureLoadBalancerModalState
> {
  public static defaultProps: Partial<IAzureLoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
    isNew: true,
  };

  public static show(props: IAzureLoadBalancerModalProps): Promise<any> {
    return ReactModal.show(AzureLoadBalancerModal, props, { dialogClassName: 'modal-lg' });
  }

  private mounted = false;
  private transformer = new AzureLoadBalancerTransformer(null);

  constructor(props: IAzureLoadBalancerModalProps) {
    super(props);
    const application = this.getApplication();
    const taskMonitor = new TaskMonitor({
      application,
      title: `${props.isNew ? 'Creating' : 'Updating'} your load balancer`,
      modalInstance: TaskMonitor.modalInstanceEmulation(
        () => this.props.closeModal(),
        () => this.props.dismissModal(),
      ),
      onTaskComplete: this.onTaskComplete,
    });

    this.state = {
      accounts: [],
      accountsLoaded: !props.isNew,
      existingLoadBalancerNames: [],
      loadBalancer: this.initializeLoadBalancer(),
      regions: [],
      selectedSubnets: [],
      selectedVnets: [],
      taskMonitor,
    };
  }

  public componentDidMount(): void {
    this.mounted = true;
    if (this.props.isNew) {
      this.updateName();
      this.initializeCreateMode();
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private getApplication(): Application {
    return (this.props.app || this.props.application) as Application;
  }

  private getLoadBalancerType(): string {
    return getAzureLoadBalancerTypeChoice(this.props.loadBalancer, this.props.loadBalancerType).type;
  }

  private initializeLoadBalancer(): any {
    if (this.props.loadBalancer) {
      const sourceLoadBalancer = this.props.loadBalancer;
      let loadBalancer =
        this.props.forPipelineConfig && !sourceLoadBalancer.elb
          ? { ...sourceLoadBalancer }
          : this.transformer.convertLoadBalancerForEditing(sourceLoadBalancer);
      loadBalancer = liftAzureLoadBalancerSessionPersistence(
        loadBalancer,
        this.getLoadBalancerType(),
        sourceLoadBalancer,
      );
      if (this.props.isNew) {
        const nameParts = NameUtils.parseLoadBalancerName(loadBalancer.name);
        loadBalancer.stack = nameParts.stack;
        loadBalancer.detail = nameParts.freeFormDetails;
        delete loadBalancer.name;
      }
      return applyAzureLoadBalancerTypeDefaults(loadBalancer, this.getLoadBalancerType());
    }

    return applyAzureLoadBalancerTypeDefaults(
      this.transformer.constructNewLoadBalancerTemplate(this.getApplication()),
      this.getLoadBalancerType(),
    );
  }

  private initializeCreateMode(): void {
    AccountService.listAccounts('azure').then((accounts) => {
      if (!this.mounted) {
        return;
      }
      this.setState({ accounts, accountsLoaded: true }, this.accountUpdated);
    });
  }

  private onTaskComplete = (): void => {
    const application = this.getApplication();
    application.loadBalancers.refresh();
    application.loadBalancers.onNextRefresh(this, this.onApplicationRefresh);
  };

  private onApplicationRefresh = (): void => {
    if (!this.mounted) {
      return;
    }

    const { loadBalancer } = this.state;
    this.props.closeModal();
    const newStateParams = {
      name: loadBalancer.name,
      accountId: loadBalancer.credentials,
      region: loadBalancer.region,
      provider: 'azure',
    };

    if (!ReactInjector.$state.includes('**.loadBalancerDetails')) {
      ReactInjector.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      ReactInjector.$state.go('^.loadBalancerDetails', newStateParams);
    }
  };

  private getName(): string {
    const { loadBalancer } = this.state;
    return [this.getApplication().name, loadBalancer.stack || '', loadBalancer.detail || '']
      .join('-')
      .replace(/-+$/, '');
  }

  private updateName = (): void => {
    if (!this.props.isNew) {
      return;
    }
    this.updateLoadBalancer({ name: this.getName() });
  };

  private accountUpdated = (): void => {
    AccountService.getRegionsForAccount(this.state.loadBalancer.credentials).then((regions) => {
      if (!this.mounted) {
        return;
      }
      this.setState({ regions }, this.regionUpdated);
    });
  };

  private regionUpdated = (): void => {
    this.updateName();
    this.updateLoadBalancerNames();
    this.vnetUpdated();
  };

  private updateLoadBalancerNames = (): void => {
    const loadBalancers = this.getApplication().getDataSource?.('loadBalancers');
    if (!loadBalancers || !this.props.isNew) {
      return;
    }

    const { credentials, region } = this.state.loadBalancer;
    loadBalancers.refresh(true).then(() => {
      if (!this.mounted) {
        return;
      }
      const existingLoadBalancerNames = (loadBalancers.data || [])
        .filter((loadBalancer: any) => loadBalancer.account === credentials && loadBalancer.region === region)
        .map((loadBalancer: any) => loadBalancer.name);
      this.setState({ existingLoadBalancerNames });
    });
  };

  private vnetUpdated = (): void => {
    const { credentials, region } = this.state.loadBalancer;
    this.setState((state) => ({
      loadBalancer: {
        ...state.loadBalancer,
        selectedVnet: null,
        vnet: null,
        vnetResourceGroup: null,
      },
      selectedVnets: [],
    }));

    NetworkReader.listNetworks().then((networks: any) => {
      if (!this.mounted) {
        return;
      }
      const azureNetworks = Array.isArray(networks) ? networks : networks.azure || [];
      const selectedVnets = azureNetworks.filter(
        (vnet: IAzureVnet) => vnet.account === credentials && vnet.region === region,
      );
      this.setState({ selectedVnets });
    });

    this.subnetUpdated();
  };

  private subnetUpdated = (): void => {
    this.setState((state) => ({
      loadBalancer: {
        ...state.loadBalancer,
        selectedSubnet: null,
        subnet: null,
      },
      selectedSubnets: [],
    }));
  };

  private selectedVnetChanged = (vnetName: string): void => {
    const selectedVnet = findAzureVnetBySelectValue(this.state.selectedVnets, vnetName);
    const selectedSubnets = (selectedVnet?.subnets || []).filter((subnet) =>
      (subnet.devices || []).every((device) => !device || device.type === 'applicationGateways'),
    );

    this.setState((state) => ({
      loadBalancer: {
        ...state.loadBalancer,
        selectedVnet,
        vnet: selectedVnet?.name || null,
        vnetResourceGroup: selectedVnet?.resourceGroup || null,
        selectedSubnet: null,
        subnet: null,
      },
      selectedSubnets,
    }));
  };

  private selectedSubnetChanged = (subnetName: string): void => {
    const selectedSubnet = this.state.selectedSubnets.find((subnet) => subnet.name === subnetName) || null;
    this.updateLoadBalancer({ selectedSubnet, subnet: selectedSubnet?.name || null });
  };

  private updateLoadBalancer(values: any, callback?: () => void): void {
    this.setState((state) => ({ loadBalancer: { ...state.loadBalancer, ...values } }), callback);
  }

  private updateField = (field: string, value: any): void => {
    this.updateLoadBalancer(
      { [field]: value },
      field === 'credentials' ? this.accountUpdated : field === 'region' ? this.regionUpdated : undefined,
    );
  };

  private updateNamePart = (field: string, value: string): void => {
    this.updateLoadBalancer({ [field]: value }, this.updateName);
  };

  private updateProbe = (field: string, value: any): void => {
    const probes = [{ ...(this.state.loadBalancer.probes?.[0] || {}), [field]: value }];
    this.updateLoadBalancer({ probes });
  };

  private updateRule = (index: number, field: string, value: any): void => {
    const loadBalancingRules = [...(this.state.loadBalancer.loadBalancingRules || [])];
    loadBalancingRules[index] = { ...loadBalancingRules[index], [field]: value };
    this.updateLoadBalancer({ loadBalancingRules });
  };

  private updateFirstRule = (field: string, value: any): void => {
    this.updateRule(0, field, value);
  };

  private addListener = (): void => {
    const loadBalancingRules = [
      ...(this.state.loadBalancer.loadBalancingRules || []),
      { protocol: listenerProtocolOptions(this.getLoadBalancerType())[0] },
    ];
    this.updateLoadBalancer({ loadBalancingRules });
  };

  private removeListener = (index: number): void => {
    const loadBalancingRules = [...(this.state.loadBalancer.loadBalancingRules || [])];
    loadBalancingRules.splice(index, 1);
    this.updateLoadBalancer({ loadBalancingRules });
  };

  private submit = (): void => {
    const application = this.getApplication();
    const descriptor = this.props.isNew ? 'Create' : 'Update';
    const errors = validateAzureLoadBalancerForSubmit(this.state.loadBalancer, this.getLoadBalancerType(), {
      existingLoadBalancerNames: this.state.existingLoadBalancerNames,
      isNew: this.props.isNew,
    });
    if (errors.length) {
      return;
    }
    const loadBalancer = normalizeAzureLoadBalancerForSubmit(this.state.loadBalancer, this.getLoadBalancerType());

    if (this.props.forPipelineConfig) {
      this.props.closeModal(loadBalancer);
      return;
    }

    const params = {
      cloudProvider: 'azure',
      appName: application.name,
      clusterName: loadBalancer.clusterName,
      resourceGroupName: loadBalancer.clusterName,
      loadBalancerName: loadBalancer.name,
    };

    this.setState({ loadBalancer });
    this.state.taskMonitor.submit(() =>
      LoadBalancerWriter.upsertLoadBalancer(loadBalancer, application, descriptor, params),
    );
  };

  private renderInput(label: string, field: string, type = 'text', onChange = this.updateField) {
    const value = formatInputValue(this.state.loadBalancer[field]);
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            type={type}
            value={value}
            onChange={(event) => onChange(field, event.target.value)}
          />
        </div>
      </div>
    );
  }

  private renderSelect(
    label: string,
    value: string,
    values: any[],
    onChange: (value: string) => void,
    getLabel = (item: any) => item.name || item,
    getValue = getLabel,
  ) {
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            value={value || ''}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">Select...</option>
            {values.map((item) => (
              <option key={getValue(item)} value={getValue(item)}>
                {getLabel(item)}
              </option>
            ))}
          </select>
        </div>
      </div>
    );
  }

  private renderSkuSelect() {
    return this.renderSelect(
      'SKU',
      this.state.loadBalancer.sku,
      validSkus,
      (value) => this.updateField('sku', value),
      (item) => item,
    );
  }

  private renderProbeInput(label: string, field: string, type = 'text') {
    const probe = this.state.loadBalancer.probes?.[0] || {};
    const value = formatInputValue(probe[field]);
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            min={type === 'number' ? 0 : undefined}
            type={type}
            value={value}
            onChange={(event) =>
              this.updateProbe(field, type === 'number' ? parseOptionalNumber(event.target.value) : event.target.value)
            }
          />
        </div>
      </div>
    );
  }

  private renderFirstRuleInput(label: string, field: string, type = 'text') {
    const rule = this.state.loadBalancer.loadBalancingRules?.[0] || {};
    const value = formatInputValue(rule[field]);
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            min={type === 'number' ? 0 : undefined}
            type={type}
            value={value}
            onChange={(event) =>
              this.updateFirstRule(
                field,
                type === 'number' ? parseOptionalNumber(event.target.value) : event.target.value,
              )
            }
          />
        </div>
      </div>
    );
  }

  public render() {
    const { dismissModal, isNew } = this.props;
    const {
      accounts,
      accountsLoaded,
      existingLoadBalancerNames,
      loadBalancer,
      regions,
      selectedSubnets,
      selectedVnets,
      taskMonitor,
    } = this.state;
    const probe = loadBalancer.probes?.[0] || {};
    const submitting = taskMonitor.submitting;
    const loadBalancerType = this.getLoadBalancerType();
    const validationErrors = validateAzureLoadBalancerForSubmit(loadBalancer, loadBalancerType, {
      existingLoadBalancerNames,
      isNew,
    });
    const listenerOptions = listenerProtocolOptions(loadBalancerType);
    const probeOptions = probeProtocolOptions(loadBalancerType);
    const showCreateFields = shouldRenderCreateFields(isNew);
    const showNetworkFields = shouldRenderNetworkFields(isNew, loadBalancerType);
    const showSkuField = shouldRenderSkuField(isNew, loadBalancerType);

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>
            {isNew ? `Create New ${loadBalancerType}` : `Edit ${loadBalancerType} ${loadBalancer.name}`}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <form className="form-horizontal" onSubmit={(event) => event.preventDefault()}>
            {showCreateFields && this.renderInput('Stack', 'stack', 'text', this.updateNamePart)}
            {showCreateFields && this.renderInput('Detail', 'detail', 'text', this.updateNamePart)}
            {showCreateFields && this.renderInput('Name', 'name')}
            {showCreateFields &&
              this.renderSelect('Account', loadBalancer.credentials, accounts, (value) =>
                this.updateField('credentials', value),
              )}
            {showCreateFields &&
              this.renderSelect('Region', loadBalancer.region, regions, (value) => this.updateField('region', value))}
            {showNetworkFields &&
              this.renderSelect(
                'VNet',
                loadBalancer.selectedVnet ? getAzureVnetSelectValue(loadBalancer.selectedVnet) : '',
                selectedVnets,
                this.selectedVnetChanged,
                getAzureVnetSelectLabel,
                getAzureVnetSelectValue,
              )}
            {showNetworkFields &&
              this.renderSelect(
                'Subnet',
                loadBalancer.selectedSubnet?.name || loadBalancer.subnet,
                selectedSubnets,
                this.selectedSubnetChanged,
              )}
            {this.renderInput('DNS Name', 'dnsName')}
            {showSkuField && this.renderSkuSelect()}
            <h4>Listeners</h4>
            {(loadBalancer.loadBalancingRules || []).map((rule: any, index: number) => (
              <div className="well" key={index}>
                <div className="form-group">
                  <label className="col-md-3 control-label">Protocol</label>
                  <div className="col-md-7">
                    <select
                      className="form-control input-sm"
                      value={rule.protocol || listenerOptions[0]}
                      onChange={(event) => this.updateRule(index, 'protocol', event.target.value)}
                    >
                      {listenerOptions.map((protocol) => (
                        <option key={protocol} value={protocol}>
                          {protocol}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 control-label">External Port</label>
                  <div className="col-md-3">
                    <input
                      className="form-control input-sm"
                      min={0}
                      type="number"
                      value={formatInputValue(rule.externalPort)}
                      onChange={(event) =>
                        this.updateRule(index, 'externalPort', parseOptionalNumber(event.target.value))
                      }
                    />
                  </div>
                  <label className="col-md-2 control-label">Backend Port</label>
                  <div className="col-md-3">
                    <input
                      className="form-control input-sm"
                      min={0}
                      type="number"
                      value={formatInputValue(rule.backendPort)}
                      onChange={(event) =>
                        this.updateRule(index, 'backendPort', parseOptionalNumber(event.target.value))
                      }
                    />
                  </div>
                </div>
                <button className="btn btn-link" type="button" onClick={() => this.removeListener(index)}>
                  Remove Listener
                </button>
              </div>
            ))}
            <button className="btn btn-default" type="button" onClick={this.addListener}>
              Add Listener
            </button>
            <h4>Health Check</h4>
            <div className="form-group">
              <label className="col-md-3 control-label">Probe Protocol</label>
              <div className="col-md-7">
                <select
                  className="form-control input-sm"
                  value={probe.probeProtocol || probeOptions[0]}
                  onChange={(event) => this.updateProbe('probeProtocol', event.target.value)}
                >
                  {probeOptions.map((protocol) => (
                    <option key={protocol} value={protocol}>
                      {protocol}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="form-group">
              <label className="col-md-3 control-label">Probe Port</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  min={0}
                  type="number"
                  value={formatInputValue(probe.probePort)}
                  onChange={(event) => this.updateProbe('probePort', parseOptionalNumber(event.target.value))}
                />
              </div>
            </div>
            {probe.probeProtocol !== 'TCP' && (
              <div className="form-group">
                <label className="col-md-3 control-label">Probe Path</label>
                <div className="col-md-7">
                  <input
                    className="form-control input-sm"
                    value={probe.probePath || ''}
                    onChange={(event) => this.updateProbe('probePath', event.target.value)}
                  />
                </div>
              </div>
            )}
            <h4>Advanced Settings</h4>
            {this.renderProbeInput('Probe Interval', 'probeInterval', 'number')}
            {this.renderProbeInput('Unhealthy Threshold', 'unhealthyThreshold', 'number')}
            {isAzureLoadBalancerType(loadBalancerType) &&
              this.renderFirstRuleInput('Idle Timeout', 'idleTimeout', 'number')}
            {isAzureLoadBalancerType(loadBalancerType) && (
              <div className="form-group">
                <label className="col-md-3 control-label">Session Persistence</label>
                <div className="col-md-7">
                  <select
                    className="form-control input-sm"
                    value={loadBalancer.sessionPersistence || 'None'}
                    onChange={(event) => this.updateField('sessionPersistence', event.target.value)}
                  >
                    <option value="None">None</option>
                    <option value="Client IP">Client IP</option>
                    <option value="Client IP and protocol">Client IP and protocol</option>
                  </select>
                </div>
              </div>
            )}
            {!isAzureLoadBalancerType(loadBalancerType) && this.renderProbeInput('Probe Timeout', 'timeout', 'number')}
          </form>
          {!!validationErrors.length && (
            <div className="alert alert-danger">
              {validationErrors.map((error) => (
                <div key={error}>{error}</div>
              ))}
            </div>
          )}
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" disabled={submitting} onClick={dismissModal} type="button">
            Cancel
          </button>
          <SubmitButton
            isDisabled={(isNew && !accountsLoaded) || submitting || !!validationErrors.length}
            isNew={isNew}
            label={isNew ? 'Create' : 'Update'}
            onClick={this.submit}
            submitting={submitting}
          />
        </Modal.Footer>
      </>
    );
  }
}
