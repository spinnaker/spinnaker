import * as React from 'react';
import { Modal, Button } from 'react-bootstrap';
import * as classNames from 'classnames';
import { bindAll } from 'lodash';

import { InstanceReader, IInstanceConsoleOutput, IInstanceMultiOutputLog } from 'core/instance/InstanceReader';
import { IPodNameProvider } from '../PodNameProvider';

// IJobManifestPodLogs is the data needed to get logs
export interface IJobManifestPodLogsProps {
  account: string;
  location: string;
  linkName: string;
  podNameProvider: IPodNameProvider;
}

export interface IJobManifestPodLogsState {
  containerLogs: IInstanceMultiOutputLog[];
  showModal: boolean;
  selectedContainerLog: IInstanceMultiOutputLog;
  errorMessage: string;
}

// JobManifestPodLogs exposes pod logs for Job type manifests in the deploy manifest stage
export class JobManifestPodLogs extends React.Component<IJobManifestPodLogsProps, IJobManifestPodLogsState> {
  constructor(props: IJobManifestPodLogsProps) {
    super(props);
    this.state = {
      containerLogs: [],
      selectedContainerLog: null,
      showModal: false,
      errorMessage: null,
    };
    bindAll(this, ['open', 'close', 'onClick']);
  }

  private canShow(): boolean {
    const { podNameProvider } = this.props;
    return podNameProvider.getPodName() !== '';
  }

  private resourceRegion(): string {
    return this.props.location;
  }

  private podName(): string {
    const { podNameProvider } = this.props;
    return `pod ${podNameProvider.getPodName()}`;
  }

  public close() {
    this.setState({ showModal: false });
  }

  public open() {
    this.setState({ showModal: true });
  }

  public onClick() {
    const { account } = this.props;
    const region = this.resourceRegion();
    InstanceReader.getConsoleOutput(account, region, this.podName(), 'kubernetes')
      .then((response: IInstanceConsoleOutput) => {
        this.setState({
          containerLogs: response.output as IInstanceMultiOutputLog[],
          selectedContainerLog: response.output[0] as IInstanceMultiOutputLog,
        });
        this.open();
      })
      .catch((exception: any) => {
        this.setState({ errorMessage: exception.data.message });
        this.open();
      });
  }

  public selectLog(log: IInstanceMultiOutputLog) {
    this.setState({ selectedContainerLog: log });
  }

  public render() {
    const { showModal, containerLogs, errorMessage, selectedContainerLog } = this.state;
    if (this.canShow()) {
      return (
        <div>
          <a onClick={this.onClick} className="clickable">
            {this.props.linkName}
          </a>
          <Modal show={showModal} onHide={this.close} dialogClassName="modal-lg modal-fullscreen">
            <Modal.Header closeButton={true}>
              <Modal.Title>Console Output: {this.podName()} </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              {containerLogs.length && (
                <>
                  <ul className="tabs-basic console-output-tabs">
                    {containerLogs.map(log => (
                      <li
                        key={log.name}
                        className={classNames('console-output-tab', {
                          selected: log.name === selectedContainerLog.name,
                        })}
                        onClick={() => this.selectLog(log)}
                      >
                        {log.name}
                      </li>
                    ))}
                  </ul>
                  <pre className="body-small">{selectedContainerLog.output}</pre>
                </>
              )}
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
