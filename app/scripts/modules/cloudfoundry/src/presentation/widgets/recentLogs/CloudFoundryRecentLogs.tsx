import { bindAll } from 'lodash';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import { IInstanceConsoleOutput, InstanceReader } from '@spinnaker/core';

export enum CloudFoundryRecentLogsType {
  APP = 'app',
  TASK = 'task',
}

export interface ICloudFoundryRecentLogsProps {
  account: string;
  region: string;
  resourceDisplayName: string;
  resourceGuid: string;
  logsType: CloudFoundryRecentLogsType;
  resourceInstanceIndex?: number;
}

export interface ICloudFoundryRecentLogsState {
  logs: string;
  showModal: boolean;
  errorMessage: string;
}

export class CloudFoundryRecentLogs extends React.Component<
  ICloudFoundryRecentLogsProps,
  ICloudFoundryRecentLogsState
> {
  constructor(props: ICloudFoundryRecentLogsProps) {
    super(props);
    this.state = {
      logs: '',
      showModal: false,
      errorMessage: null,
    };
    bindAll(this, ['open', 'close', 'onClick']);
  }

  private canShow(): boolean {
    const { resourceGuid } = this.props;
    return resourceGuid !== ''; // or null?
  }

  private generateResourceId(): string {
    const { resourceGuid, logsType, resourceInstanceIndex } = this.props;
    const optionalInstanceIndex = logsType === CloudFoundryRecentLogsType.APP ? `:${resourceInstanceIndex}` : '';
    return `${logsType}:${resourceGuid}${optionalInstanceIndex}`;
  }

  public close() {
    this.setState({ showModal: false });
  }

  public open() {
    this.setState({ showModal: true });
  }

  public onClick() {
    const { account, region } = this.props;
    InstanceReader.getConsoleOutput(account, region, this.generateResourceId(), 'cloudfoundry')
      .then((response: IInstanceConsoleOutput) => {
        this.setState({
          logs: response.output as string,
        });
        this.open();
      })
      .catch((exception: any) => {
        this.setState({ errorMessage: exception.data.message });
        this.open();
      });
  }

  public render() {
    const { showModal, logs, errorMessage } = this.state;
    if (this.canShow()) {
      return (
        <div>
          <a onClick={this.onClick} className="clickable">
            Recent
          </a>
          <Modal show={showModal} onHide={this.close} dialogClassName="modal-lg modal-fullscreen">
            <Modal.Header closeButton={true}>
              <Modal.Title>Recent logs</Modal.Title>
              <h5>
                CloudFoundry {this.props.logsType.valueOf()} name: {this.props.resourceDisplayName}
              </h5>
            </Modal.Header>
            <Modal.Body>
              {logs.length > 0 && (
                <>
                  <pre className="body-small">{logs}</pre>
                </>
              )}

              {logs.length === 0 && <>No logs returned.</>}

              {errorMessage && <pre className="body-small">{errorMessage}</pre>}
            </Modal.Body>
            <Modal.Footer>
              <Button onClick={this.close}>Close</Button>
            </Modal.Footer>
          </Modal>
        </div>
      );
    } else {
      return null;
    }
  }
}
