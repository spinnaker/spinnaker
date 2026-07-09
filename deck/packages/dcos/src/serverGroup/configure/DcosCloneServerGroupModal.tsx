import React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { ModalClose, noop, ReactModal } from '@spinnaker/core';

export const dcosCloneServerGroupFormFields = [
  'name',
  'credentials',
  'account',
  'dcosCluster',
  'region',
  'cluster',
  'instances',
  'cpus',
  'mem',
  'disk',
  'gpus',
  'docker.image.registry',
  'docker.image.organization',
  'docker.image.repository',
  'docker.image.tag',
  'labels',
  'env',
];

export interface IDcosCloneServerGroupModalProps extends IModalComponentProps {
  application: Application;
  command: any;
  title?: string;
}

interface IDcosCloneServerGroupModalState {
  command: any;
  advancedJson: string;
}

export class DcosCloneServerGroupModal extends React.Component<
  IDcosCloneServerGroupModalProps,
  IDcosCloneServerGroupModalState
> {
  public static defaultProps: Partial<IDcosCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IDcosCloneServerGroupModalProps): Promise<any> {
    return ReactModal.show(DcosCloneServerGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  public state = {
    command: this.normalizeCommand(this.props.command || {}),
    advancedJson: JSON.stringify(this.normalizeCommand(this.props.command || {}), null, 2),
  };

  private normalizeCommand(command: any) {
    const cluster = command.dcosCluster || command.region || command.cluster;
    return {
      cloudProvider: 'dcos',
      provider: 'dcos',
      ...command,
      credentials: command.credentials || command.account,
      account: command.account || command.credentials,
      dcosCluster: cluster,
      region: cluster,
      cluster,
      docker: { ...(command.docker || {}), image: { ...(command.docker?.image || {}) } },
      labels: command.labels || {},
      env: command.env || {},
    };
  }

  private updateCommand = (updater: (command: any) => any) => {
    this.setState((state: any) => {
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

  private updateDockerImage = (field: string, value: any) => {
    this.updateCommand((command) => ({
      ...command,
      docker: { ...(command.docker || {}), image: { ...(command.docker?.image || {}), [field]: value } },
    }));
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

  private close = () => this.props.closeModal(this.state.command);

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
    const command: any = this.state.command;
    const values = command[field] || {};
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
    const { dismissModal, title } = this.props;
    const command: any = this.state.command;
    const image = command.docker?.image || {};
    return (
      <>
        <ModalClose dismiss={dismissModal} />
        <div className="modal-header">
          <h3 className="modal-title">{title || 'Create DC/OS Server Group'}</h3>
        </div>
        <div className="modal-body">
          <div className="form-horizontal">
            {this.renderInput('Name', command.name || command.id, (value) => this.updateField('name', value))}
            {this.renderInput('Account', command.credentials || command.account, (value) =>
              this.updateCommand((current) => ({ ...current, credentials: value, account: value })),
            )}
            {this.renderInput(
              'DC/OS Cluster',
              command.dcosCluster || command.region || command.cluster,
              this.updateCluster,
            )}
            {this.renderInput(
              'Instances',
              command.instances,
              (value) => this.updateField('instances', Number(value)),
              'number',
            )}
            {this.renderInput('CPUs', command.cpus, (value) => this.updateField('cpus', Number(value)), 'number')}
            {this.renderInput('Memory', command.mem, (value) => this.updateField('mem', Number(value)), 'number')}
            {this.renderInput('Disk', command.disk, (value) => this.updateField('disk', Number(value)), 'number')}
            {this.renderInput('GPUs', command.gpus, (value) => this.updateField('gpus', Number(value)), 'number')}
            {this.renderInput('Docker Registry', image.registry, (value) => this.updateDockerImage('registry', value))}
            {this.renderInput('Docker Organization', image.organization, (value) =>
              this.updateDockerImage('organization', value),
            )}
            {this.renderInput('Docker Image', image.repository, (value) => this.updateDockerImage('repository', value))}
            {this.renderInput('Docker Tag', image.tag, (value) => this.updateDockerImage('tag', value))}
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
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" type="button" onClick={() => dismissModal()}>
            Cancel
          </button>
          <button className="btn btn-primary" type="button" onClick={this.close}>
            Use Command
          </button>
        </div>
      </>
    );
  }
}
