import * as React from 'react';
import { Modal, Button } from 'react-bootstrap';
import { IManifest, IManifestEvent, InstanceReader } from '@spinnaker/core';
import { get, trim, bindAll } from 'lodash';

// IJobManifestPodLogs is the data needed to get logs
export interface IJobManifestPodLogsProps {
  manifest: IManifest;
  manifestEvent: IManifestEvent;
  linkName: string;
}

export interface IJobManifestPodLogsState {
  showModal: boolean;
  output: string;
}

// JobManifestPodLogs exposes pod logs for Job type manifests in the deploy manifest stage
export class JobManifestPodLogs extends React.Component<IJobManifestPodLogsProps, IJobManifestPodLogsState> {
  constructor(props: IJobManifestPodLogsProps) {
    super(props);
    this.state = {
      output: '',
      showModal: false,
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
      .then((response: any) => {
        this.setState({ output: response.output });
        this.open();
      })
      .catch((exception: any) => {
        this.setState({ output: exception.data.message });
        this.open();
      });
  }

  public render() {
    const { showModal, output } = this.state;

    if (this.canShow()) {
      return (
        <div>
          <a onClick={this.onClick} className="clickable">
            {this.props.linkName}
          </a>
          <Modal show={showModal} onHide={this.close}>
            <Modal.Header closeButton={true}>
              <Modal.Title>Console Output: {this.podName()} </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <pre>{output}</pre>
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
