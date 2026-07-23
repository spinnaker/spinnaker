import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps, ISecurityGroup } from '@spinnaker/core';
import {
  AngularServices,
  FirewallLabels,
  ModalClose,
  noop,
  ReactModal,
  SecurityGroupWriter,
  SubmitButton,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

export type AmazonSecurityGroupModalMode = 'create' | 'edit' | 'clone';

interface IAmazonSecurityGroupModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  credentials?: string;
  isNew?: boolean;
  mode?: AmazonSecurityGroupModalMode;
  region?: string;
  securityGroup?: any;
}

interface IIngressRule {
  account?: string;
  accountId?: string;
  accountName?: string;
  cidr?: string;
  endPort: number;
  existing?: boolean;
  id?: string;
  name?: string;
  startPort: number;
  type: string;
  vpcId?: string;
}

interface IAmazonSecurityGroupModalState {
  securityGroup: any;
  taskMonitor: TaskMonitor;
}

function accountFor(securityGroup: any): string {
  return (
    securityGroup.credentials || securityGroup.accountName || securityGroup.accountId || securityGroup.account || ''
  );
}

function ipIngressFromInboundRules(securityGroup: any): IIngressRule[] {
  return (securityGroup.inboundRules || [])
    .filter((rule: any) => rule.range)
    .reduce((rules: IIngressRule[], rule: any) => {
      (rule.portRanges || []).forEach((portRange: any) => {
        rules.push({
          cidr: rule.range.ip + rule.range.cidr,
          type: rule.protocol,
          startPort: portRange.startPort,
          endPort: portRange.endPort,
        });
      });
      return rules;
    }, []);
}

function securityGroupIngressFromInboundRules(securityGroup: any, mode: AmazonSecurityGroupModalMode): IIngressRule[] {
  return (securityGroup.inboundRules || [])
    .filter((rule: any) => rule.securityGroup)
    .reduce((rules: IIngressRule[], rule: any) => {
      (rule.portRanges || []).forEach((portRange: any) => {
        const referencedGroup = rule.securityGroup;
        const existing = mode === 'edit';
        rules.push({
          ...(existing
            ? {
                account: referencedGroup.account,
                accountId: referencedGroup.accountId,
                accountName: referencedGroup.accountName || referencedGroup.accountId,
                existing: true,
                id: referencedGroup.id,
                vpcId: referencedGroup.vpcId !== securityGroup.vpcId ? referencedGroup.vpcId : null,
              }
            : {}),
          name: referencedGroup.inferredName ? null : referencedGroup.name,
          type: rule.protocol,
          startPort: portRange.startPort,
          endPort: portRange.endPort,
        });
      });
      return rules;
    }, []);
}

export function initializeAmazonSecurityGroupForModal(props: IAmazonSecurityGroupModalProps, appName: string): any {
  const source = props.securityGroup || {};
  const account = accountFor(source) || props.credentials || '';
  const region = source.region || props.region || source.regions?.[0] || '';
  const mode = props.mode || 'create';
  const name = source.name || appName;
  const securityGroup = {
    ...source,
    account,
    accountId: source.accountId || account,
    accountName: source.accountName || account,
    credentials: account,
    description: source.description || '',
    ipIngress: source.ipIngress || ipIngressFromInboundRules(source),
    name,
    region,
    regions: [region].filter(Boolean),
    securityGroupIngress: source.securityGroupIngress || securityGroupIngressFromInboundRules(source, mode),
    vpcId: source.vpcId || '',
  };

  if (mode === 'clone') {
    delete securityGroup.id;
  }

  return securityGroup;
}

function isValidRule(rule: IIngressRule): boolean {
  return !!rule.type && rule.startPort !== undefined && rule.endPort !== undefined;
}

export function isAmazonSecurityGroupValid(securityGroup: any): boolean {
  return (
    !!securityGroup.name &&
    !!accountFor(securityGroup) &&
    !!securityGroup.region &&
    (securityGroup.ipIngress || []).every((rule: IIngressRule) => !!rule.cidr && isValidRule(rule)) &&
    (securityGroup.securityGroupIngress || []).every(
      (rule: IIngressRule) => !!(rule.name || rule.id) && isValidRule(rule),
    )
  );
}

export class AmazonSecurityGroupModal extends React.Component<
  IAmazonSecurityGroupModalProps,
  IAmazonSecurityGroupModalState
> {
  public static defaultProps: Partial<IAmazonSecurityGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
    mode: 'create',
  };

  public static show(props: IAmazonSecurityGroupModalProps): Promise<any> {
    return ReactModal.show(AmazonSecurityGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  constructor(props: IAmazonSecurityGroupModalProps) {
    super(props);
    const app = this.getApplication(props);
    this.state = {
      securityGroup: initializeAmazonSecurityGroupForModal(props, app.name),
      taskMonitor: new TaskMonitor({
        application: app,
        title: `${this.getMode(props) === 'edit' ? 'Updating' : 'Creating'} your ${FirewallLabels.get('firewall')}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal(),
          () => props.dismissModal(),
        ),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  private getApplication(props = this.props): Application {
    return (props.app || props.application) as Application;
  }

  private getMode(props = this.props): AmazonSecurityGroupModalMode {
    return props.mode || 'create';
  }

  private updateSecurityGroup = (updates: any): void => {
    this.setState(({ securityGroup }) => ({ securityGroup: { ...securityGroup, ...updates } }));
  };

  private updateRule = (
    collection: 'ipIngress' | 'securityGroupIngress',
    index: number,
    updates: Partial<IIngressRule>,
  ) => {
    const rules = this.state.securityGroup[collection].slice();
    rules[index] = { ...rules[index], ...updates };
    this.updateSecurityGroup({ [collection]: rules });
  };

  private addIpRule = (): void => {
    this.updateSecurityGroup({
      ipIngress: [
        ...this.state.securityGroup.ipIngress,
        { cidr: '0.0.0.0/0', type: 'tcp', startPort: 7001, endPort: 7001 },
      ],
    });
  };

  private addSecurityGroupRule = (): void => {
    this.updateSecurityGroup({
      securityGroupIngress: [
        ...this.state.securityGroup.securityGroupIngress,
        { name: '', type: 'tcp', startPort: 7001, endPort: 7001 },
      ],
    });
  };

  private removeRule = (collection: 'ipIngress' | 'securityGroupIngress', index: number): void => {
    this.updateSecurityGroup({
      [collection]: this.state.securityGroup[collection].filter((_rule: any, ruleIndex: number) => ruleIndex !== index),
    });
  };

  private onTaskComplete = (): void => {
    const app = this.getApplication();
    app.securityGroups?.refresh?.();
    this.props.closeModal();

    if (this.getMode() === 'edit') {
      return;
    }

    const { securityGroup } = this.state;
    AngularServices.$state.go(
      AngularServices.$state.includes('**.firewallDetails') ? '^.firewallDetails' : '.firewallDetails',
      {
        accountId: accountFor(securityGroup),
        name: securityGroup.name,
        provider: 'aws',
        region: securityGroup.region,
        vpcId: securityGroup.vpcId,
      },
    );
  };

  private submit = (): void => {
    const app = this.getApplication();
    const { securityGroup } = this.state;
    const credentials = accountFor(securityGroup);
    const command = {
      ...securityGroup,
      account: credentials,
      accountId: credentials,
      accountName: credentials,
      cloudProvider: 'aws',
      credentials,
      regions: [securityGroup.region],
    } as ISecurityGroup;
    const descriptor = this.getMode() === 'edit' ? 'Update' : this.getMode() === 'clone' ? 'Clone' : 'Create';

    this.state.taskMonitor.submit(() => SecurityGroupWriter.upsertSecurityGroup(command, app, descriptor));
  };

  private renderTextField(label: string, value: string, onChange: (value: string) => void): JSX.Element {
    return (
      <div className="form-group row">
        <label className="col-md-3 sm-label-right">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            value={value || ''}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      </div>
    );
  }

  private renderIpRules(): JSX.Element {
    const rules = this.state.securityGroup.ipIngress || [];
    return (
      <div>
        <h5>IP Ingress</h5>
        {rules.map((rule: IIngressRule, index: number) => (
          <div className="row" key={`ip-${index}`}>
            <div className="col-md-3">
              <input
                className="form-control input-sm"
                value={rule.cidr || ''}
                onChange={(event) => this.updateRule('ipIngress', index, { cidr: event.target.value })}
              />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                value={rule.type || ''}
                onChange={(event) => this.updateRule('ipIngress', index, { type: event.target.value })}
              />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={rule.startPort}
                onChange={(event) => this.updateRule('ipIngress', index, { startPort: Number(event.target.value) })}
              />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={rule.endPort}
                onChange={(event) => this.updateRule('ipIngress', index, { endPort: Number(event.target.value) })}
              />
            </div>
            <div className="col-md-1">
              <Button bsSize="xsmall" onClick={() => this.removeRule('ipIngress', index)}>
                Remove
              </Button>
            </div>
          </div>
        ))}
        <Button bsSize="small" onClick={this.addIpRule}>
          Add IP Rule
        </Button>
      </div>
    );
  }

  private renderSecurityGroupRules(): JSX.Element {
    const rules = this.state.securityGroup.securityGroupIngress || [];
    return (
      <div>
        <h5>{FirewallLabels.get('Firewall')} Ingress</h5>
        {rules.map((rule: IIngressRule, index: number) => (
          <div className="row" key={`sg-${index}`}>
            <div className="col-md-3">
              {rule.existing ? (
                <span className="form-control-static">{rule.name || rule.id}</span>
              ) : (
                <input
                  className="form-control input-sm"
                  value={rule.name || ''}
                  onChange={(event) => this.updateRule('securityGroupIngress', index, { name: event.target.value })}
                />
              )}
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                value={rule.type || ''}
                onChange={(event) => this.updateRule('securityGroupIngress', index, { type: event.target.value })}
              />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={rule.startPort}
                onChange={(event) =>
                  this.updateRule('securityGroupIngress', index, { startPort: Number(event.target.value) })
                }
              />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={rule.endPort}
                onChange={(event) =>
                  this.updateRule('securityGroupIngress', index, { endPort: Number(event.target.value) })
                }
              />
            </div>
            <div className="col-md-1">
              <Button bsSize="xsmall" onClick={() => this.removeRule('securityGroupIngress', index)}>
                Remove
              </Button>
            </div>
          </div>
        ))}
        <Button bsSize="small" onClick={this.addSecurityGroupRule}>
          Add {FirewallLabels.get('Firewall')} Rule
        </Button>
      </div>
    );
  }

  public render(): JSX.Element {
    const { securityGroup, taskMonitor } = this.state;
    const mode = this.getMode();
    const valid = isAmazonSecurityGroupValid(securityGroup);
    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={this.props.dismissModal} />
        <Modal.Header>
          <Modal.Title>
            {mode === 'edit' ? 'Edit' : mode === 'clone' ? 'Clone' : 'Create'} {FirewallLabels.get('Firewall')}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {this.renderTextField('Account', accountFor(securityGroup), (credentials) =>
            this.updateSecurityGroup({
              account: credentials,
              accountId: credentials,
              accountName: credentials,
              credentials,
            }),
          )}
          {this.renderTextField('Region', securityGroup.region, (region) =>
            this.updateSecurityGroup({ region, regions: [region] }),
          )}
          {this.renderTextField('VPC ID', securityGroup.vpcId, (vpcId) => this.updateSecurityGroup({ vpcId }))}
          {this.renderTextField('Name', securityGroup.name, (name) => this.updateSecurityGroup({ name }))}
          {this.renderTextField('Description', securityGroup.description, (description) =>
            this.updateSecurityGroup({ description }),
          )}
          <div className="well">
            <div className="row small text-bold">
              <div className="col-md-3">CIDR / {FirewallLabels.get('Firewall')}</div>
              <div className="col-md-2">Protocol</div>
              <div className="col-md-2">Start Port</div>
              <div className="col-md-2">End Port</div>
            </div>
            {this.renderIpRules()}
            {this.renderSecurityGroupRules()}
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.props.dismissModal}>Cancel</Button>
          <SubmitButton
            isDisabled={!valid}
            isFormSubmit={false}
            submitting={taskMonitor.submitting}
            onClick={this.submit}
            label={mode === 'edit' ? 'Update' : mode === 'clone' ? 'Clone' : 'Create'}
          />
        </Modal.Footer>
      </>
    );
  }
}
