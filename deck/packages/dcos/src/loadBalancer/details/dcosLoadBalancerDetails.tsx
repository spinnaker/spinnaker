import React from 'react';

import type {
  ILoadBalancerActionsProps,
  ILoadBalancerDetailsSectionProps,
  IUseDetailsHookProps,
  UseDetailsResult,
} from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  LoadBalancerWriter,
  ReactModal,
  useDataSource,
} from '@spinnaker/core';

import { DcosJsonLink, DcosLink } from '../../common/DcosDetails';
import { dcosProxyUiService } from '../../proxy/ui.service';

export const dcosLoadBalancerFormFields = [
  'name',
  'credentials',
  'account',
  'dcosCluster',
  'region',
  'cluster',
  'ports',
  'instances',
  'cpus',
  'mem',
  'labels',
  'env',
];

export function useDcosLoadBalancerDetails({
  app,
  loadBalancerParams,
  autoClose,
}: IUseDetailsHookProps): UseDetailsResult<any> {
  const dataSource = app.getDataSource('loadBalancers');
  const { data: loadBalancers, loaded, refresh, error } = useDataSource<any[]>(dataSource);
  const { name, accountId, region } = loadBalancerParams;

  const data = React.useMemo(() => {
    if (!loaded || error) {
      return undefined;
    }
    return loadBalancers.find((lb: any) => lb.name === name && lb.account === accountId && lb.region === region);
  }, [loaded, loadBalancers, error, name, accountId, region]);

  React.useEffect(() => {
    if (loaded && !error && !data) {
      autoClose();
    }
  }, [loaded, error, data, autoClose]);

  return { data, loading: !loaded, error, refetch: async () => refresh() };
}

function dcosLoadBalancerLink(loadBalancer: any): string {
  const host = loadBalancer.dcosClusterUrl || loadBalancer.dcosUrl || loadBalancer.host;
  if (!host) {
    return undefined;
  }
  return dcosProxyUiService.buildLoadBalancerLink(host, loadBalancer.account, loadBalancer.name);
}

export function DcosLoadBalancerActions({ app, loadBalancer }: ILoadBalancerActionsProps) {
  const edit = () => DcosCreateLoadBalancerModal.show({ app, loadBalancer });
  const remove = () => {
    const command = {
      cloudProvider: 'dcos',
      credentials: loadBalancer.account,
      loadBalancerName: loadBalancer.name,
      region: loadBalancer.region,
    } as any;

    ConfirmationModalService.confirm({
      header: `Really delete ${loadBalancer.name}?`,
      buttonText: `Delete ${loadBalancer.name}`,
      account: loadBalancer.account,
      taskMonitorConfig: {
        application: app,
        title: `Deleting ${loadBalancer.name}`,
      },
      submitMethod: () => LoadBalancerWriter.deleteLoadBalancer(command, app).then(() => app.loadBalancers.refresh()),
    });
  };

  return (
    <div className="dropdown">
      <button className="btn btn-sm btn-primary dropdown-toggle" data-toggle="dropdown">
        Load Balancer Actions <span className="caret" />
      </button>
      <ul className="dropdown-menu dropdown-menu-right">
        <li>
          <a className="clickable" onClick={edit}>
            Edit
          </a>
        </li>
        <li>
          <a className="clickable" onClick={remove}>
            Delete
          </a>
        </li>
      </ul>
    </div>
  );
}

export function DcosLoadBalancerInformationSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Details" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>
          <AccountTag account={loadBalancer.account} />
        </dd>
        <dt>Region</dt>
        <dd>{loadBalancer.region || '-'}</dd>
        <dt>DC/OS Cluster</dt>
        <dd>{loadBalancer.dcosCluster || '-'}</dd>
        <dt>JSON</dt>
        <dd>
          <DcosJsonLink value={loadBalancer} />
        </dd>
        <dt>DC/OS UI</dt>
        <DcosLink href={dcosLoadBalancerLink(loadBalancer)} />
      </dl>
    </CollapsibleSection>
  );
}

export function DcosLoadBalancerStatusSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const counts: any = loadBalancer.instanceCounts || {};
  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instances</dt>
        <dd>{loadBalancer.instances ?? '-'}</dd>
        <dt>Up</dt>
        <dd>{counts['up'] ?? '-'}</dd>
        <dt>Down</dt>
        <dd>{counts['down'] ?? '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export const dcosLoadBalancerDetailsSections = [DcosLoadBalancerInformationSection, DcosLoadBalancerStatusSection];

export interface IDcosCreateLoadBalancerModalProps {
  app?: any;
  application?: any;
  loadBalancer?: any;
}

interface IDcosCreateLoadBalancerModalState {
  command: any;
  advancedJson: string;
}

export class DcosCreateLoadBalancerModal extends React.Component<
  IDcosCreateLoadBalancerModalProps & any,
  IDcosCreateLoadBalancerModalState
> {
  public static show(props: IDcosCreateLoadBalancerModalProps): PromiseLike<any> {
    return ReactModal.show(DcosCreateLoadBalancerModal as any, props as any, { dialogClassName: 'modal-lg' });
  }

  public state = {
    command: this.normalizeCommand(this.props.loadBalancer || {}),
    advancedJson: JSON.stringify(this.normalizeCommand(this.props.loadBalancer || {}), null, 2),
  };

  private normalizeCommand(loadBalancer: any) {
    const cluster = loadBalancer.dcosCluster || loadBalancer.region || loadBalancer.cluster;
    return {
      cloudProvider: 'dcos',
      provider: 'dcos',
      ...loadBalancer,
      credentials: loadBalancer.credentials || loadBalancer.account,
      account: loadBalancer.account || loadBalancer.credentials,
      dcosCluster: cluster,
      region: cluster,
      cluster,
      ports: loadBalancer.ports || [],
      labels: loadBalancer.labels || {},
      env: loadBalancer.env || {},
    };
  }

  private updateCommand = (updater: (command: any) => any) => {
    this.setState((state) => {
      const command = this.normalizeCommand(updater(state.command));
      return { command, advancedJson: JSON.stringify(command, null, 2) };
    });
  };

  private updateField = (field: string, value: any) => {
    this.updateCommand((command) => ({ ...command, [field]: value }));
  };

  private updateCluster = (value: string) => {
    this.updateCommand((command) => ({ ...command, dcosCluster: value, region: value, cluster: value }));
  };

  private updatePorts = (value: string) => {
    this.updateField(
      'ports',
      value
        .split(',')
        .map((port) => port.trim())
        .filter(Boolean)
        .map(Number)
        .filter((port) => Number.isFinite(port)),
    );
  };

  private updateMap = (field: 'labels' | 'env', key: string, value: string) => {
    this.updateCommand((command) => ({ ...command, [field]: { ...(command[field] || {}), [key]: value } }));
  };

  private addMapEntry = (field: 'labels' | 'env') => {
    this.updateCommand((command) => {
      const values = { ...(command[field] || {}) };
      let index = 0;
      let key = 'key';
      while (Object.prototype.hasOwnProperty.call(values, key)) {
        index += 1;
        key = `key${index}`;
      }
      values[key] = '';
      return { ...command, [field]: values };
    });
  };

  private updateAdvancedJson = (advancedJson: string) => {
    this.setState({ advancedJson });
    try {
      this.setState({ command: this.normalizeCommand(JSON.parse(advancedJson)) });
    } catch (_error) {
      // Keep the edited JSON visible until it becomes valid again.
    }
  };

  private submit = () => {
    const app = this.props.app || this.props.application;
    const loadBalancer = this.state.command;
    const isEdit = !!this.props.loadBalancer;
    const command = {
      ...loadBalancer,
      cloudProvider: 'dcos',
      provider: 'dcos',
    };
    if (app) {
      LoadBalancerWriter.upsertLoadBalancer(command, app, isEdit ? 'Update' : 'Create')
        .then(() => app.loadBalancers.refresh())
        .then(() => this.props.closeModal?.(command))
        .catch(() => undefined);
      return;
    }
    this.props.closeModal?.(command);
  };

  private renderInput(label: string, value: any, onChange: (value: string) => void, type = 'text') {
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            type={type}
            value={value || ''}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      </div>
    );
  }

  private renderMap(label: string, field: 'labels' | 'env') {
    const values = this.state.command[field] || {};
    const keys = Object.keys(values);
    return (
      <div className="form-group">
        <label className="col-md-3 control-label">{label}</label>
        <div className="col-md-7">
          {keys.map((key) => (
            <div className="row" key={key}>
              <div className="col-md-6">
                <input className="form-control input-sm" value={key} readOnly={true} />
              </div>
              <div className="col-md-6">
                <input
                  className="form-control input-sm"
                  value={values[key] || ''}
                  onChange={(event) => this.updateMap(field, key, event.target.value)}
                />
              </div>
            </div>
          ))}
          <button className="btn btn-sm btn-default" type="button" onClick={() => this.addMapEntry(field)}>
            Add {label}
          </button>
        </div>
      </div>
    );
  }

  public render() {
    const isEdit = !!this.props.loadBalancer;
    const command = this.state.command;
    return (
      <div className="modal-body">
        <h3>{isEdit ? 'Edit DC/OS Load Balancer' : 'Create DC/OS Load Balancer'}</h3>
        <div className="form-horizontal">
          {this.renderInput('Name', command.name, (value) => this.updateField('name', value))}
          {this.renderInput('Account', command.credentials || command.account, (value) =>
            this.updateCommand((current) => ({ ...current, credentials: value, account: value })),
          )}
          {this.renderInput(
            'DC/OS Cluster',
            command.dcosCluster || command.region || command.cluster,
            this.updateCluster,
          )}
          {this.renderInput('Ports', (command.ports || []).join(', '), this.updatePorts)}
          {this.renderInput(
            'Instances',
            command.instances,
            (value) => this.updateField('instances', Number(value)),
            'number',
          )}
          {this.renderInput('CPUs', command.cpus, (value) => this.updateField('cpus', Number(value)), 'number')}
          {this.renderInput('Memory', command.mem, (value) => this.updateField('mem', Number(value)), 'number')}
          {this.renderMap('Labels', 'labels')}
          {this.renderMap('Environment', 'env')}
          <div className="form-group">
            <label className="col-md-3 control-label">Advanced JSON</label>
            <div className="col-md-7">
              <textarea
                className="form-control"
                rows={8}
                value={this.state.advancedJson}
                onChange={(event) => this.updateAdvancedJson(event.target.value)}
              />
            </div>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" type="button" onClick={() => this.props.dismissModal?.()}>
            Cancel
          </button>
          <button className="btn btn-primary" type="button" onClick={this.submit}>
            {isEdit ? 'Save' : 'Create'}
          </button>
        </div>
      </div>
    );
  }
}
