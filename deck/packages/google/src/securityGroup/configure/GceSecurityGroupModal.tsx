import React from 'react';

import { ReactModal, SecurityGroupWriter, TaskMonitor } from '@spinnaker/core';

interface IGceSecurityGroupModalProps {
  application: any;
  closeModal?: () => void;
  credentials?: string;
  dismissModal?: () => void;
}

export class GceSecurityGroupModal extends React.Component<IGceSecurityGroupModalProps, any> {
  public static show(props: IGceSecurityGroupModalProps): Promise<any> {
    return ReactModal.show(GceSecurityGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  public state: {
    allowed: Array<{ IPProtocol: string; ports: string[] }>;
    name: string;
    network: string;
    ports: string;
    sourceRanges: string;
    targetTags: string;
  } = {
    allowed: [{ IPProtocol: 'tcp', ports: [] }],
    name: this.props.application?.name || '',
    network: 'default',
    ports: '',
    sourceRanges: '',
    targetTags: '',
  };

  private csv(value: string): string[] {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }

  private isValid(): boolean {
    return !!(
      this.state.name.trim() &&
      this.state.network.trim() &&
      this.state.ports.trim() &&
      this.state.sourceRanges.trim()
    );
  }

  private submit = (): void => {
    const taskMonitor = new TaskMonitor({ application: this.props.application, title: 'Creating your firewall' });
    taskMonitor.submit(() =>
      SecurityGroupWriter.upsertSecurityGroup(
        {
          allowed: [{ IPProtocol: 'tcp', ports: this.csv(this.state.ports) }],
          application: this.props.application.name,
          cloudProvider: 'gce',
          credentials: this.props.credentials,
          name: this.state.name.trim(),
          network: this.state.network.trim(),
          region: 'global',
          sourceRanges: this.csv(this.state.sourceRanges),
          targetTags: this.csv(this.state.targetTags),
        } as any,
        this.props.application,
        'Create',
        {},
      ),
    );
  };

  public render(): JSX.Element {
    return (
      <div className="modal-content">
        <div className="modal-header">
          <button type="button" className="close" onClick={this.props.dismissModal}>
            <span>&times;</span>
          </button>
          <h3>Create Google Firewall</h3>
        </div>
        <div className="modal-body">
          <div className="form-horizontal">
            <div className="form-group">
              <label className="col-md-3 control-label">Name</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.setState({ name: event.target.value })}
                  value={this.state.name}
                />
              </div>
            </div>
            <div className="form-group">
              <label className="col-md-3 control-label">Network</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.setState({ network: event.target.value })}
                  value={this.state.network}
                />
              </div>
            </div>
            <div className="form-group">
              <label className="col-md-3 control-label">Target tags</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.setState({ targetTags: event.target.value })}
                  placeholder="web, api"
                  value={this.state.targetTags}
                />
              </div>
            </div>
            <div className="form-group">
              <label className="col-md-3 control-label">Source CIDRs</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.setState({ sourceRanges: event.target.value })}
                  placeholder="10.0.0.0/8"
                  value={this.state.sourceRanges}
                />
              </div>
            </div>
            <div className="form-group">
              <label className="col-md-3 control-label">TCP ports</label>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.setState({ ports: event.target.value })}
                  placeholder="443, 7001-7002"
                  value={this.state.ports}
                />
              </div>
            </div>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" onClick={this.props.dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" disabled={!this.isValid()} onClick={this.submit} type="button">
            Submit
          </button>
        </div>
      </div>
    );
  }
}
