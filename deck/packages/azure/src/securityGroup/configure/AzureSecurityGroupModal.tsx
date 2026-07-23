import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps, IRouterInjectedProps, ISecurityGroup } from '@spinnaker/core';
import {
  AccountService,
  ModalClose,
  NetworkReader,
  noop,
  ReactModal,
  SubmitButton,
  TaskMonitor,
  TaskMonitorWrapper,
  withRouter,
} from '@spinnaker/core';

import { AzureSecurityGroupWriter } from '../securityGroup.write.service';

export type AzureSecurityGroupModalMode = 'create' | 'edit' | 'clone';

interface IAzureSecurityGroupModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  credentials?: string;
  mode: AzureSecurityGroupModalMode;
  region?: string;
  securityGroup?: any;
}

interface IAzureSecurityGroupModalState {
  accounts: any[];
  regions: any[];
  securityGroup: any;
  selectedSubnets: IAzureSubnet[];
  selectedVnets: IAzureVnet[];
  taskMonitor: TaskMonitor;
}

interface IAzureVnet {
  account: string;
  name: string;
  region: string;
  resourceGroup?: string;
  subnets?: IAzureSubnet[];
}

interface IAzureSubnet {
  name: string;
}

const defaultRule = {
  access: 'Allow',
  destinationAddressPrefix: '*',
  destinationPortRange: '*',
  destinationPortRanges: [] as string[],
  destinationPorts: ['*'],
  destPortRanges: '*',
  direction: 'Inbound',
  protocol: 'Tcp',
  sourceAddressPrefix: '*',
  sourceAddressPrefixes: [] as string[],
  sourceCidrs: ['*'],
  sourceIPCIDRRanges: '*',
  sourcePortRange: '*',
};

function parseDelimitedList(value: any): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean);
  }
  if (value === null || value === undefined || value === '') {
    return [];
  }
  return String(value)
    .split(/[\n,]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function firstPopulatedList(values: any[], fallback: string[] = []): string[] {
  for (const value of values) {
    const parsed = parseDelimitedList(value);
    if (parsed.length) {
      return parsed;
    }
  }
  return fallback;
}

function hasOwnProperty(value: any, property: string): boolean {
  return Object.prototype.hasOwnProperty.call(value, property);
}

function destinationPortsForRule(rule: any, fallback: string[] = []): string[] {
  if (hasOwnProperty(rule, 'destinationPorts')) {
    return parseDelimitedList(rule.destinationPorts);
  }

  return firstPopulatedList(
    [rule.destPortRanges, rule.destinationPortRanges, rule.destinationPortRange, rule.destinationPortRangeModel],
    fallback,
  );
}

function sourceCidrsForRule(rule: any, fallback: string[] = []): string[] {
  if (hasOwnProperty(rule, 'sourceCidrs')) {
    return parseDelimitedList(rule.sourceCidrs);
  }

  return firstPopulatedList(
    [rule.sourceIPCIDRRanges, rule.sourceAddressPrefixes, rule.sourceAddressPrefix, rule.sourceAddressPrefixModel],
    fallback,
  );
}

function isValidPortRange(value: string): boolean {
  if (value === '*') {
    return true;
  }

  const bounds = value.split('-');
  if (bounds.length > 2) {
    return false;
  }

  const ports = bounds.map((bound) => {
    if (!/^\d+$/.test(bound)) {
      return null;
    }
    const port = Number(bound);
    return port >= 0 && port <= 65535 ? port : null;
  });
  if (ports.some((port) => port === null)) {
    return false;
  }

  return bounds.length === 1 || (ports[0] as number) <= (ports[1] as number);
}

function isValidSourceCidr(value: string): boolean {
  if (value === '*') {
    return true;
  }

  const match = value.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?:\/(\d{1,2}))?$/);
  if (!match) {
    return false;
  }

  const octets = match.slice(1, 5).map(Number);
  const prefix = match[5] ? Number(match[5]) : null;
  return octets.every((octet) => octet >= 0 && octet <= 255) && (prefix === null || (prefix >= 0 && prefix <= 32));
}

function buildAzureSecurityGroupName(applicationName: string, detail: any): string {
  const detailText = String(detail || '').trim();
  return detailText ? `${applicationName}-${detailText}` : applicationName;
}

function detailFromSecurityGroupName(name: string, applicationName: string): string {
  const prefix = `${applicationName}-`;
  return name?.startsWith(prefix) ? name.slice(prefix.length) : '';
}

function securityGroupAccount(securityGroup: any): string {
  return String(
    securityGroup.accountId || securityGroup.account || securityGroup.accountName || securityGroup.credentials || '',
  );
}

function matchesDefinedCoordinate(left: any, right: any): boolean {
  if (!left || !right) {
    return true;
  }
  return String(left).toLowerCase() === String(right).toLowerCase();
}

function getExistingSecurityGroups(application?: Application): any[] {
  const securityGroups = application?.securityGroups?.data || application?.getDataSource?.('securityGroups')?.data;
  return Array.isArray(securityGroups) ? securityGroups : [];
}

function isDuplicateSecurityGroupName(securityGroup: any, application?: Application): boolean {
  const name = String(securityGroup.name || '').toLowerCase();
  const account = securityGroupAccount(securityGroup);
  const region = securityGroup.region;

  if (!name || !account) {
    return false;
  }

  return getExistingSecurityGroups(application).some((existingSecurityGroup) => {
    return (
      String(existingSecurityGroup.name || '').toLowerCase() === name &&
      matchesDefinedCoordinate(securityGroupAccount(existingSecurityGroup), account) &&
      matchesDefinedCoordinate(existingSecurityGroup.region, region)
    );
  });
}

export function isAzureSecurityGroupValid(
  securityGroup: any,
  mode: AzureSecurityGroupModalMode = 'edit',
  application?: Application,
): boolean {
  if (!securityGroup.name || !securityGroup.accountId || !securityGroup.region) {
    return false;
  }

  if (mode !== 'edit') {
    if (!String(securityGroup.detail || '').trim()) {
      return false;
    }
    if (isDuplicateSecurityGroupName(securityGroup, application)) {
      return false;
    }
  }

  return (securityGroup.securityRules || []).every((rule: any) => {
    const destinationPorts = destinationPortsForRule(rule);
    const sourceCidrs = sourceCidrsForRule(rule);

    return (
      destinationPorts.length > 0 &&
      sourceCidrs.length > 0 &&
      destinationPorts.every(isValidPortRange) &&
      sourceCidrs.every(isValidSourceCidr)
    );
  });
}

function cloneRule(rule: any): any {
  const destinationPorts = destinationPortsForRule(rule, defaultRule.destinationPorts);
  const sourceCidrs = sourceCidrsForRule(rule, defaultRule.sourceCidrs);

  return {
    ...defaultRule,
    ...rule,
    destinationPortRangeModel: rule.destinationPortRangeModel || destinationPorts.join(','),
    destinationPorts,
    destPortRanges: rule.destPortRanges || destinationPorts.join(','),
    sourceAddressPrefixModel: rule.sourceAddressPrefixModel || sourceCidrs.join(','),
    sourceCidrs,
    sourceIPCIDRRanges: rule.sourceIPCIDRRanges || sourceCidrs.join(','),
  };
}

function normalizePriorities(rules: any[]): any[] {
  return rules.map((rule, index) => ({ ...rule, priority: 100 + index }));
}

export function addSecurityRule(rules: any[]): any[] {
  return normalizePriorities([...rules, { ...defaultRule, name: `rule-${rules.length + 1}` }]);
}

export function removeSecurityRule(rules: any[], index: number): any[] {
  return normalizePriorities(rules.filter((_rule, ruleIndex) => ruleIndex !== index));
}

export function moveSecurityRule(rules: any[], fromIndex: number, toIndex: number): any[] {
  const nextRules = [...rules];
  const [rule] = nextRules.splice(fromIndex, 1);
  nextRules.splice(toIndex, 0, rule);
  return normalizePriorities(nextRules);
}

export function normalizeAzureSecurityGroupForSubmit(securityGroup: ISecurityGroup & Record<string, any>): any {
  return {
    ...securityGroup,
    type: 'upsertSecurityGroup',
    securityRules: (securityGroup.securityRules || []).map((rule: any, index: number) => {
      const normalized = {
        ...rule,
        priority: rule.priority ?? 100 + index,
      };
      const destinationPorts = destinationPortsForRule(rule);
      const sourceCidrs = sourceCidrsForRule(rule);

      delete normalized.destinationPorts;
      delete normalized.destPortRanges;
      delete normalized.sourceCidrs;
      delete normalized.sourceIPCIDRRanges;
      delete normalized.destinationPortRange;
      delete normalized.destinationPortRanges;
      delete normalized.destinationPortRangeModel;
      delete normalized.sourceAddressPrefix;
      delete normalized.sourceAddressPrefixes;
      delete normalized.sourceAddressPrefixModel;

      if (destinationPorts.length > 1) {
        normalized.destinationPortRanges = destinationPorts;
      } else if (destinationPorts.length === 1) {
        normalized.destinationPortRange = destinationPorts[0];
      }

      if (sourceCidrs.length > 1) {
        normalized.sourceAddressPrefixes = sourceCidrs;
      } else if (sourceCidrs.length === 1) {
        normalized.sourceAddressPrefix = sourceCidrs[0];
      }

      return normalized;
    }),
  };
}

export function getAzureVnetSelectValue(vnet: IAzureVnet): string {
  return `${vnet.name}|${vnet.resourceGroup || ''}`;
}

function getAzureVnetSelectLabel(vnet: IAzureVnet): string {
  return vnet.resourceGroup ? `${vnet.name} (${vnet.resourceGroup})` : vnet.name;
}

function findAzureVnetBySelectValue(vnets: IAzureVnet[], value: string): IAzureVnet | null {
  return vnets.find((vnet) => getAzureVnetSelectValue(vnet) === value) || null;
}

export function initializeAzureSecurityGroupForModal(
  props: IAzureSecurityGroupModalProps,
  applicationName: string,
): any {
  const source = props.securityGroup || {};
  const selectedVnet = source.selectedVnet || null;
  const selectedSubnet = source.selectedSubnet || null;
  const accountId =
    source.accountId || source.account || source.accountName || source.credentials || props.credentials || '';
  const region = source.region || props.region || source.regions?.[0] || '';
  const detail = source.detail || detailFromSecurityGroupName(source.name, applicationName);
  const securityGroup = {
    ...source,
    accountId,
    credentials: source.credentials || accountId,
    detail,
    name:
      props.mode === 'edit'
        ? source.name || buildAzureSecurityGroupName(applicationName, detail)
        : buildAzureSecurityGroupName(applicationName, detail),
    region,
    securityRules: (source.securityRules || source.inboundRules || []).map(cloneRule),
    selectedSubnet,
    selectedVnet,
    subnet: source.subnet || selectedSubnet?.name || '',
    vnet: source.vnet || selectedVnet?.name || '',
    vnetResourceGroup: source.vnetResourceGroup || selectedVnet?.resourceGroup || '',
    vpcId: source.vpcId || '',
  };

  if (props.mode === 'clone') {
    delete securityGroup.id;
  }

  return securityGroup;
}

export class AzureSecurityGroupModalComponent extends React.Component<
  IAzureSecurityGroupModalProps & IRouterInjectedProps,
  IAzureSecurityGroupModalState
> {
  public static defaultProps: Partial<IAzureSecurityGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
    mode: 'create',
  };

  public static show(props: IAzureSecurityGroupModalProps): Promise<any> {
    return ReactModal.show(AzureSecurityGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  constructor(props: IAzureSecurityGroupModalProps & IRouterInjectedProps) {
    super(props);
    const application = this.getApplication(props);
    this.state = {
      accounts: [],
      regions: [],
      securityGroup: initializeAzureSecurityGroupForModal(props, application.name),
      selectedSubnets: props.securityGroup?.selectedVnet?.subnets || [],
      selectedVnets: props.securityGroup?.selectedVnet ? [props.securityGroup.selectedVnet] : [],
      taskMonitor: new TaskMonitor({
        application,
        title: `${props.mode === 'edit' ? 'Updating' : 'Creating'} your security group`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal(),
          () => props.dismissModal(),
        ),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    AccountService.listAccounts('azure').then((accounts) => this.setState({ accounts }));
    if (this.state.securityGroup.accountId || this.state.securityGroup.credentials) {
      this.accountUpdated(false);
    } else if (this.state.securityGroup.region) {
      this.vnetUpdated();
    }
  }

  private getApplication(props = this.props): Application {
    return (props.app || props.application) as Application;
  }

  public onTaskComplete = (): void => {
    const application = this.getApplication();
    application.securityGroups?.refresh?.();

    if (this.props.mode === 'edit') {
      this.props.closeModal();
      return;
    }

    const showNewSecurityGroup = (): void => {
      const { securityGroup } = this.state;
      this.props.closeModal();
      this.props.stateService.go(
        this.props.stateService.includes('**.firewallDetails') ? '^.firewallDetails' : '.firewallDetails',
        {
          accountId: securityGroup.credentials || securityGroup.accountId || securityGroup.accountName,
          name: securityGroup.name,
          provider: 'azure',
          region: securityGroup.region,
          vpcId: securityGroup.vpcId,
        },
      );
    };

    if (application.securityGroups?.onNextRefresh) {
      application.securityGroups.onNextRefresh(null, showNewSecurityGroup);
    } else {
      showNewSecurityGroup();
    }
  };

  private updateSecurityGroup(values: any, callback?: () => void): void {
    this.setState((state) => ({ securityGroup: { ...state.securityGroup, ...values } }), callback);
  }

  private updateField = (field: string, value: string): void => {
    if (field === 'detail') {
      this.updateSecurityGroup({ detail: value, name: buildAzureSecurityGroupName(this.getApplication().name, value) });
      return;
    }

    this.updateSecurityGroup(
      { [field]: value },
      field === 'accountId' ? this.accountUpdated : field === 'region' ? this.vnetUpdated : undefined,
    );
  };

  public accountUpdated = (clearNetworkSelections = true): void => {
    const account = this.state.securityGroup.accountId || this.state.securityGroup.credentials;
    if (!account) {
      return;
    }

    AccountService.getRegionsForAccount(account).then((regions) => {
      const region = this.state.securityGroup.region || regions[0]?.name || regions[0] || '';
      this.setState(
        (state) => ({
          regions,
          securityGroup: { ...state.securityGroup, accountId: account, credentials: account, region },
        }),
        () => this.vnetUpdated(clearNetworkSelections),
      );
    });
  };

  public vnetUpdated = (clearNetworkSelections = true): void => {
    const account = this.state.securityGroup.accountId || this.state.securityGroup.credentials;
    const region = this.state.securityGroup.region;
    if (clearNetworkSelections) {
      this.setState((state) => ({
        securityGroup: {
          ...state.securityGroup,
          selectedSubnet: null,
          selectedVnet: null,
          subnet: null,
          vnet: null,
          vnetResourceGroup: null,
          vpcId: null,
        },
        selectedSubnets: [],
        selectedVnets: [],
      }));
    }

    if (!account || !region) {
      return;
    }

    NetworkReader.listNetworks().then((networks: any) => {
      const azureNetworks = Array.isArray(networks) ? networks : networks.azure || [];
      this.setState({
        selectedVnets: azureNetworks.filter((vnet: IAzureVnet) => vnet.account === account && vnet.region === region),
      });
    });
  };

  private selectedVnetChanged = (vnetValue: string): void => {
    const selectedVnet = findAzureVnetBySelectValue(this.state.selectedVnets, vnetValue);
    this.setState((state) => ({
      securityGroup: {
        ...state.securityGroup,
        selectedSubnet: null,
        selectedVnet,
        subnet: '',
        vnet: selectedVnet?.name || '',
        vnetResourceGroup: selectedVnet?.resourceGroup || '',
        vpcId: selectedVnet?.name || '',
      },
      selectedSubnets: selectedVnet?.subnets || [],
    }));
  };

  private selectedSubnetChanged = (subnetName: string): void => {
    const selectedSubnet = this.state.selectedSubnets.find((subnet) => subnet.name === subnetName) || null;
    this.updateSecurityGroup({ selectedSubnet, subnet: selectedSubnet?.name || '' });
  };

  private updateRule = (index: number, field: string, value: any): void => {
    const securityRules = [...(this.state.securityGroup.securityRules || [])];
    securityRules[index] = { ...securityRules[index], [field]: value };
    this.updateSecurityGroup({ securityRules });
  };

  private addRule = (): void => {
    this.updateSecurityGroup({ securityRules: addSecurityRule(this.state.securityGroup.securityRules || []) });
  };

  private removeRule = (index: number): void => {
    this.updateSecurityGroup({
      securityRules: removeSecurityRule(this.state.securityGroup.securityRules || [], index),
    });
  };

  private moveRule = (index: number, offset: number): void => {
    this.updateSecurityGroup({
      securityRules: moveSecurityRule(this.state.securityGroup.securityRules || [], index, index + offset),
    });
  };

  public submit(): void {
    const application = this.getApplication();
    const securityGroup = normalizeAzureSecurityGroupForSubmit(this.state.securityGroup);
    const descriptor = this.props.mode === 'edit' ? 'Update' : this.props.mode === 'clone' ? 'Clone' : 'Create';
    const params = {
      appName: application.name,
      cloudProvider: 'azure',
      credentials: securityGroup.accountId,
      region: securityGroup.region,
      regions: [securityGroup.region],
      securityGroupName: securityGroup.name,
      subnet: securityGroup.subnet || null,
      vpcId: securityGroup.vpcId || 'null',
    };

    this.state.taskMonitor.submit(() =>
      AzureSecurityGroupWriter.upsertSecurityGroup(securityGroup, application, descriptor, params),
    );
  }

  private renderInput(label: string, field: string) {
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            value={this.state.securityGroup[field] || ''}
            onChange={(e) => this.updateField(field, e.target.value)}
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
          <select className="form-control input-sm" value={value || ''} onChange={(e) => onChange(e.target.value)}>
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

  private renderNamePreview(duplicateName: boolean) {
    return (
      <div className="form-group">
        <div className={`col-md-12 well ${duplicateName ? 'alert-danger' : 'alert-info'}`}>
          <strong>Your security group will be named:</strong> <span>{this.state.securityGroup.name}</span>
          {duplicateName && <div>There is already a security group with that name in this account and region.</div>}
        </div>
      </div>
    );
  }

  public render() {
    const { dismissModal, mode } = this.props;
    const { accounts, regions, securityGroup, selectedSubnets, selectedVnets, taskMonitor } = this.state;
    const submitting = taskMonitor.submitting;
    const title =
      mode === 'edit'
        ? `Edit ${securityGroup.name}`
        : mode === 'clone'
        ? `Clone ${securityGroup.name}`
        : 'Create Security Group';
    const showLocationFields = mode !== 'edit';
    const duplicateName = showLocationFields && isDuplicateSecurityGroupName(securityGroup, this.getApplication());

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <form className="form-horizontal" onSubmit={(e) => e.preventDefault()}>
            {showLocationFields && (
              <>
                {this.renderNamePreview(duplicateName)}
                {this.renderInput('Detail', 'detail')}
                {this.renderInput('Description', 'description')}
                {this.renderSelect(
                  'Account',
                  securityGroup.accountId,
                  accounts,
                  (value) => this.updateField('accountId', value),
                  (item) => item.name || item,
                )}
                {this.renderSelect(
                  'Region',
                  securityGroup.region,
                  regions,
                  (value) => this.updateField('region', value),
                  (item) => item.name || item,
                )}
                {this.renderSelect(
                  'VNet',
                  securityGroup.selectedVnet ? getAzureVnetSelectValue(securityGroup.selectedVnet) : '',
                  selectedVnets,
                  this.selectedVnetChanged,
                  getAzureVnetSelectLabel,
                  getAzureVnetSelectValue,
                )}
                {this.renderSelect(
                  'Subnet',
                  securityGroup.selectedSubnet?.name || securityGroup.subnet,
                  selectedSubnets,
                  this.selectedSubnetChanged,
                )}
              </>
            )}
            <h4>Inbound Rules</h4>
            {(securityGroup.securityRules || []).map((rule: any, index: number) => (
              <div className="well" key={index}>
                <div className="form-group">
                  <label className="col-md-3 control-label">Name</label>
                  <div className="col-md-7">
                    <input
                      className="form-control input-sm"
                      value={rule.name || ''}
                      onChange={(e) => this.updateRule(index, 'name', e.target.value)}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 control-label">Source CIDRs</label>
                  <div className="col-md-7">
                    <textarea
                      className="form-control input-sm"
                      value={(rule.sourceCidrs || []).join(', ')}
                      onChange={(e) => this.updateRule(index, 'sourceCidrs', parseDelimitedList(e.target.value))}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 control-label">Destination Ports</label>
                  <div className="col-md-7">
                    <input
                      className="form-control input-sm"
                      value={(rule.destinationPorts || []).join(', ')}
                      onChange={(e) => this.updateRule(index, 'destinationPorts', parseDelimitedList(e.target.value))}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 control-label">Protocol</label>
                  <div className="col-md-3">
                    <select
                      className="form-control input-sm"
                      value={rule.protocol || 'Tcp'}
                      onChange={(e) => this.updateRule(index, 'protocol', e.target.value)}
                    >
                      <option value="Tcp">Tcp</option>
                      <option value="Udp">Udp</option>
                      <option value="*">Any</option>
                    </select>
                  </div>
                </div>
                <button
                  className="btn btn-link"
                  disabled={index === 0}
                  type="button"
                  onClick={() => this.moveRule(index, -1)}
                >
                  Move Up
                </button>
                <button
                  className="btn btn-link"
                  disabled={index === securityGroup.securityRules.length - 1}
                  type="button"
                  onClick={() => this.moveRule(index, 1)}
                >
                  Move Down
                </button>
                <button className="btn btn-link" type="button" onClick={() => this.removeRule(index)}>
                  Remove Rule
                </button>
              </div>
            ))}
            <button className="btn btn-default" type="button" onClick={this.addRule}>
              Add Rule
            </button>
          </form>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" disabled={submitting} onClick={dismissModal} type="button">
            Cancel
          </button>
          <SubmitButton
            isDisabled={submitting || !isAzureSecurityGroupValid(securityGroup, mode, this.getApplication())}
            isNew={mode !== 'edit'}
            label={mode === 'edit' ? 'Update' : mode === 'clone' ? 'Clone' : 'Create'}
            onClick={() => this.submit()}
            submitting={submitting}
          />
        </Modal.Footer>
      </>
    );
  }
}

export const AzureSecurityGroupModal = Object.assign(
  withRouter<IAzureSecurityGroupModalProps & IRouterInjectedProps>(AzureSecurityGroupModalComponent),
  { show: AzureSecurityGroupModalComponent.show },
);
