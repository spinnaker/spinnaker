import React from 'react';

import type { DeckRuntimeServices, IRouterInjectedProps, ISecurityGroupsByAccountSourceData } from '@spinnaker/core';
import {
  DeckRuntimeContext,
  FirewallLabels,
  ModalClose,
  ReactModal,
  SecurityGroupWriter,
  SubmitButton,
  TaskMonitor,
  TaskMonitorWrapper,
  withRouter,
} from '@spinnaker/core';

export type GceSecurityGroupModalMode = 'create' | 'edit' | 'clone';

interface IGceSecurityGroupModalProps {
  app?: any;
  application?: any;
  closeModal?: () => void;
  credentials?: string;
  dismissModal?: () => void;
  mode?: GceSecurityGroupModalMode;
  securityGroup?: any;
}

interface IGceIngressRule {
  endPort?: number;
  startPort?: number;
  type: string;
}

interface IGceSecurityGroupModalState {
  securityGroupInventory?: ISecurityGroupsByAccountSourceData;
  securityGroupInventoryLoaded: boolean;
  securityGroupValidationError?: string;
  securityGroup: any;
  taskMonitor: TaskMonitor;
}

const PROTOCOLS = ['tcp', 'udp', 'icmp', 'esp', 'ah', 'sctp'];
const PORT_PROTOCOLS = ['tcp', 'udp', 'sctp'];

function parseList(value: any): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => (typeof item === 'object' ? item.value : item))
      .filter((item) => item !== undefined && item !== null && `${item}`.trim())
      .map((item) => `${item}`.trim());
  }
  if (typeof value === 'string') {
    const unwrapped = value.startsWith('[') && value.endsWith(']') ? value.slice(1, -1) : value;
    return unwrapped
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return value ? [`${value}`] : [];
}

function sourceRangesFrom(securityGroup: any): string[] {
  const ipRangeRuleCidrs = (securityGroup.ipRangeRules || [])
    .map((rule: any) => {
      const ip = rule.range?.ip;
      return ip ? `${ip}${rule.range?.cidr || ''}` : '';
    })
    .filter(Boolean);
  return Array.from(new Set([...parseList(securityGroup.sourceRanges), ...ipRangeRuleCidrs]));
}

function normalizePortRange(portRange: any): { startPort?: number; endPort?: number } {
  if (typeof portRange === 'string' || typeof portRange === 'number') {
    const [startPort, endPort = startPort] = `${portRange}`.split('-');
    return { startPort: Number(startPort), endPort: Number(endPort) };
  }
  return {
    startPort: portRange?.startPort === undefined ? undefined : Number(portRange.startPort),
    endPort: portRange?.endPort === undefined ? undefined : Number(portRange.endPort),
  };
}

function ingressRulesFrom(securityGroup: any): IGceIngressRule[] {
  if ((securityGroup.ipIngress || []).length) {
    return securityGroup.ipIngress.map((rule: any) => ({
      type: rule.type || rule.protocol || rule.ipProtocol,
      ...normalizePortRange(rule),
    }));
  }

  const inboundRules =
    securityGroup.ipIngressRules ||
    securityGroup.inboundRules ||
    securityGroup.allowed ||
    securityGroup.ipRangeRules ||
    [];
  const rules = inboundRules.reduce((result: IGceIngressRule[], rule: any) => {
    const type = rule.type || rule.protocol || rule.ipProtocol || rule.IPProtocol;
    const portRanges = rule.portRanges || rule.ports || [];
    if (portRanges.length) {
      portRanges.forEach((portRange: any) => result.push({ type, ...normalizePortRange(portRange) }));
    } else if (type) {
      result.push({ type });
    }
    return result;
  }, []);

  const seen = new Set<string>();
  return rules.filter((rule: IGceIngressRule) => {
    const key = `${rule.type}:${rule.startPort ?? ''}:${rule.endPort ?? ''}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function accountFor(securityGroup: any, credentials?: string): string {
  return (
    securityGroup.credentials ||
    securityGroup.accountName ||
    securityGroup.accountId ||
    securityGroup.account ||
    credentials ||
    ''
  );
}

function normalizedCoordinate(value: any): string {
  return String(value || '')
    .trim()
    .toLowerCase();
}

function isDuplicateSecurityGroupName(
  securityGroup: any,
  securityGroupInventory?: ISecurityGroupsByAccountSourceData,
): boolean {
  const name = normalizedCoordinate(securityGroup.name);
  const account = normalizedCoordinate(accountFor(securityGroup));
  const accountInventoryKey = Object.keys(securityGroupInventory || {}).find(
    (inventoryAccount) => normalizedCoordinate(inventoryAccount) === account,
  );
  const existingSecurityGroups = accountInventoryKey
    ? securityGroupInventory?.[accountInventoryKey]?.gce?.global || []
    : [];

  return existingSecurityGroups.some((existingSecurityGroup: any) => {
    return normalizedCoordinate(existingSecurityGroup.name) === name;
  });
}

export function initializeGceSecurityGroupForModal(props: IGceSecurityGroupModalProps, applicationName: string): any {
  const source = props.securityGroup || {};
  const mode = props.mode || 'create';
  const credentials = accountFor(source, props.credentials);
  const securityGroup = {
    ...source,
    accountId: source.accountId || credentials,
    accountName: source.accountName || credentials,
    credentials,
    description: source.description || '',
    ipIngress: ingressRulesFrom(source),
    name: source.name || applicationName || '',
    network: source.network || source.vpcId || 'default',
    region: 'global',
    sourceRanges: sourceRangesFrom(source),
    sourceTags: parseList(source.sourceTags),
    targetTags: parseList(source.targetTags),
  };

  if (mode === 'create' && !securityGroup.ipIngress.length) {
    securityGroup.ipIngress = [{ type: 'tcp' }];
  }
  if (mode === 'clone') {
    securityGroup.id = undefined;
    securityGroup.name = '';
    delete securityGroup.selfLink;
  }

  return securityGroup;
}

export class GceSecurityGroupModalComponent extends React.Component<
  IGceSecurityGroupModalProps & IRouterInjectedProps,
  IGceSecurityGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private mounted = false;

  public static show(props: IGceSecurityGroupModalProps, runtimeServices: DeckRuntimeServices): Promise<any> {
    return ReactModal.show(GceSecurityGroupModal, props, { dialogClassName: 'modal-lg' }, runtimeServices);
  }

  public constructor(props: IGceSecurityGroupModalProps & IRouterInjectedProps) {
    super(props);
    const application = this.getApplication(props);
    const mode = props.mode || 'create';
    this.state = {
      securityGroup: initializeGceSecurityGroupForModal(props, application?.name),
      securityGroupInventoryLoaded: mode === 'edit',
      taskMonitor: new TaskMonitor({
        application,
        title: `${mode === 'edit' ? 'Updating' : 'Creating'} your ${FirewallLabels.get('firewall')}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal?.(),
          () => props.dismissModal?.(),
        ),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    this.mounted = true;
    if (this.getMode() === 'edit') {
      return;
    }

    this.context.services.securityGroupReader.getAllSecurityGroups().then(
      (securityGroupInventory) => {
        if (this.mounted) {
          this.setState({ securityGroupInventory, securityGroupInventoryLoaded: true });
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ securityGroupValidationError: 'Unable to validate firewall name.' });
        }
      },
    );
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private getApplication(props = this.props): any {
    return props.application || props.app;
  }

  private getMode(): GceSecurityGroupModalMode {
    return this.props.mode || 'create';
  }

  private onTaskComplete = (): void => {
    const securityGroups = this.getApplication()?.securityGroups;
    if (this.getMode() === 'edit') {
      securityGroups?.refresh?.();
      this.props.closeModal?.();
      return;
    }

    const showNewSecurityGroup = (): void => {
      const { securityGroup } = this.state;
      this.props.closeModal?.();
      this.props.stateService.go(
        this.props.stateService.includes('**.firewallDetails') ? '^.firewallDetails' : '.firewallDetails',
        {
          accountId: accountFor(securityGroup),
          name: securityGroup.name.trim(),
          provider: 'gce',
          region: 'global',
          vpcId: (securityGroup.network || securityGroup.vpcId).trim(),
        },
      );
    };

    if (securityGroups?.onNextRefresh) {
      securityGroups.onNextRefresh(null, showNewSecurityGroup);
      securityGroups.refresh?.();
      return;
    }

    const refreshResult = securityGroups?.refresh?.();
    if (refreshResult?.then) {
      refreshResult.then(showNewSecurityGroup);
    } else {
      showNewSecurityGroup();
    }
  };

  private updateSecurityGroup = (updates: any): void => {
    this.setState(({ securityGroup }) => ({ securityGroup: { ...securityGroup, ...updates } }));
  };

  private updateRule = (index: number, updates: Partial<IGceIngressRule>): void => {
    const ipIngress = this.state.securityGroup.ipIngress.slice();
    ipIngress[index] = { ...ipIngress[index], ...updates };
    this.updateSecurityGroup({ ipIngress });
  };

  private addRule = (): void => {
    this.updateSecurityGroup({
      ipIngress: [...this.state.securityGroup.ipIngress, { type: 'tcp', startPort: 7001, endPort: 7001 }],
    });
  };

  private removeRule = (index: number): void => {
    this.updateSecurityGroup({
      ipIngress: this.state.securityGroup.ipIngress.filter((_rule: IGceIngressRule, ruleIndex: number) => {
        return ruleIndex !== index;
      }),
    });
  };

  private isValid(): boolean {
    const { securityGroup, securityGroupInventory, securityGroupInventoryLoaded } = this.state;
    const nameIsAvailable =
      this.getMode() === 'edit' ||
      (securityGroupInventoryLoaded && !isDuplicateSecurityGroupName(securityGroup, securityGroupInventory));
    return !!(
      securityGroup.name.trim() &&
      nameIsAvailable &&
      accountFor(securityGroup) &&
      securityGroup.network.trim() &&
      securityGroup.ipIngress.length &&
      (securityGroup.sourceRanges.length || securityGroup.sourceTags.length) &&
      securityGroup.ipIngress.every((rule: IGceIngressRule) => {
        return (
          rule.type &&
          (!PORT_PROTOCOLS.includes(rule.type) || (rule.startPort !== undefined && rule.endPort !== undefined))
        );
      })
    );
  }

  private submit = (): void => {
    const application = this.getApplication();
    const { securityGroup } = this.state;
    const credentials = accountFor(securityGroup);
    const allowed = securityGroup.ipIngress.map((rule: IGceIngressRule) => {
      const serializedRule: { ipProtocol: string; portRanges?: string[] } = { ipProtocol: rule.type };
      if (rule.startPort !== undefined && rule.endPort !== undefined) {
        serializedRule.portRanges = [`${rule.startPort}-${rule.endPort}`];
      }
      return serializedRule;
    });
    const command = {
      ...securityGroup,
      accountId: securityGroup.accountId || credentials,
      accountName: securityGroup.accountName || credentials,
      allowed,
      application: application.name,
      cloudProvider: 'gce',
      credentials,
      name: securityGroup.name.trim(),
      network: securityGroup.network.trim(),
      region: 'global',
      sourceRanges: Array.from(new Set(securityGroup.sourceRanges)),
      sourceTags: Array.from(new Set(securityGroup.sourceTags)),
      targetTags: Array.from(new Set(securityGroup.targetTags)),
    };
    const mode = this.getMode();
    const descriptor = mode === 'edit' ? 'Update' : mode === 'clone' ? 'Clone' : 'Create';

    this.state.taskMonitor.submit(() =>
      SecurityGroupWriter.upsertSecurityGroup(command as any, application, descriptor),
    );
  };

  private renderTextField(
    label: string,
    value: string,
    onChange: (value: string) => void,
    readOnly = false,
  ): JSX.Element {
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            onChange={(event) => onChange(event.target.value)}
            readOnly={readOnly}
            value={value || ''}
          />
        </div>
      </div>
    );
  }

  private renderRules(): JSX.Element {
    return (
      <div className="form-group">
        <div className="col-md-10 col-md-offset-1">
          <table className="table table-condensed packed">
            <thead>
              <tr>
                <th>Protocol</th>
                <th>Start Port</th>
                <th>End Port</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {this.state.securityGroup.ipIngress.map((rule: IGceIngressRule, index: number) => (
                <tr key={index}>
                  <td>
                    <select
                      className="form-control input-sm"
                      onChange={(event) => this.updateRule(index, { type: event.target.value })}
                      value={rule.type}
                    >
                      {PROTOCOLS.map((protocol) => (
                        <option key={protocol} value={protocol}>
                          {protocol.toUpperCase()}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input
                      className="form-control input-sm"
                      min="0"
                      onChange={(event) =>
                        this.updateRule(index, {
                          startPort: event.target.value === '' ? undefined : Number(event.target.value),
                        })
                      }
                      type="number"
                      value={rule.startPort === undefined ? '' : rule.startPort}
                    />
                  </td>
                  <td>
                    <input
                      className="form-control input-sm"
                      min="0"
                      onChange={(event) =>
                        this.updateRule(index, {
                          endPort: event.target.value === '' ? undefined : Number(event.target.value),
                        })
                      }
                      type="number"
                      value={rule.endPort === undefined ? '' : rule.endPort}
                    />
                  </td>
                  <td>
                    <button className="btn btn-link" onClick={() => this.removeRule(index)} type="button">
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button className="btn btn-block add-new" onClick={this.addRule} type="button">
            <span className="glyphicon glyphicon-plus-sign" /> Add New Protocol and Port Range
          </button>
        </div>
      </div>
    );
  }

  public render(): JSX.Element {
    const { securityGroup, securityGroupInventoryLoaded, securityGroupValidationError, taskMonitor } = this.state;
    const mode = this.getMode();
    const editing = mode === 'edit';
    const heading = `${mode === 'edit' ? 'Edit' : mode === 'clone' ? 'Clone' : 'Create'} ${FirewallLabels.get(
      'Firewall',
    )}`;

    return (
      <div className="modal-content">
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={this.props.dismissModal} />
        <div className="modal-header">
          <h3>{heading}</h3>
        </div>
        <div className="modal-body">
          <div className="form-horizontal">
            {this.renderTextField('Name', securityGroup.name, (name) => this.updateSecurityGroup({ name }), editing)}
            {!editing && !securityGroupInventoryLoaded && (
              <div
                className={securityGroupValidationError ? 'security-group-validation-error text-danger' : 'help-block'}
              >
                {securityGroupValidationError || 'Checking firewall name availability...'}
              </div>
            )}
            {this.renderTextField('Description', securityGroup.description, (description) =>
              this.updateSecurityGroup({ description }),
            )}
            {this.renderTextField(
              'Account',
              accountFor(securityGroup),
              (credentials) =>
                this.updateSecurityGroup({
                  accountId: credentials,
                  accountName: credentials,
                  credentials,
                }),
              editing,
            )}
            {this.renderTextField(
              'Network',
              securityGroup.network,
              (network) => this.updateSecurityGroup({ network }),
              editing,
            )}
            {this.renderTextField('Target tags', securityGroup.targetTags.join(', '), (targetTags) =>
              this.updateSecurityGroup({ targetTags: parseList(targetTags) }),
            )}
            {this.renderTextField('Source tags', securityGroup.sourceTags.join(', '), (sourceTags) =>
              this.updateSecurityGroup({ sourceTags: parseList(sourceTags) }),
            )}
            {this.renderTextField('Source CIDRs', securityGroup.sourceRanges.join(', '), (sourceRanges) =>
              this.updateSecurityGroup({ sourceRanges: parseList(sourceRanges) }),
            )}
            {this.renderRules()}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" onClick={this.props.dismissModal} type="button">
            Cancel
          </button>
          <SubmitButton
            isDisabled={!this.isValid()}
            isFormSubmit={false}
            label={mode === 'edit' ? 'Update' : mode === 'clone' ? 'Clone' : 'Create'}
            onClick={this.submit}
            submitting={taskMonitor.submitting}
          />
        </div>
      </div>
    );
  }
}

export const GceSecurityGroupModal = Object.assign(withRouter(GceSecurityGroupModalComponent), {
  show: GceSecurityGroupModalComponent.show,
});
