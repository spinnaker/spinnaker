import * as React from 'react';
import { Modal, Button } from 'react-bootstrap';
import * as classNames from 'classnames';

import { InstanceReader, IInstanceConsoleOutput, IInstanceMultiOutputLog } from 'core/instance/InstanceReader';

import { IManifestEvent, IManifest } from 'core/domain/IManifest';

import { get, trim, bindAll } from 'lodash';

// IJobManifestPodLogs is the data needed to get logs
export interface IJobManifestPodLogsProps {
  manifest: IManifest;
  manifestEvent: IManifestEvent;
  linkName: string;
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
    const { manifest, manifestEvent } = this.props;
    return (
      !!manifest.manifest &&
      !!manifest.manifest.status &&
      !!manifestEvent &&
      !!manifestEvent.message.startsWith('Created pod') &&
      manifest.manifest.kind.toLowerCase() === 'job'
    );
  }

  private resourceRegion(): string {
    return trim(
      get(this.props, ['manifest', 'manifest', 'metadata', 'annotations', 'artifact.spinnaker.io/location'], ''),
    );
  }

  private podName(): string {
    return `pod ${trim(this.props.manifestEvent.message.split(':')[1])}`;
  }

  public close() {
    this.setState({ showModal: false });
  }

  public open() {
    this.setState({ showModal: true });
  }

  public onClick() {
    const { manifest } = this.props;
    const region = this.resourceRegion();
    InstanceReader.getConsoleOutput(manifest.account, region, this.podName(), 'kubernetes')
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
