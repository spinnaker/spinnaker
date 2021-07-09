import AnsiUp from 'ansi_up';
import classNames from 'classnames';
import DOMPurify from 'dompurify';
import { bindAll } from 'lodash';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import { IPodNameProvider } from '../PodNameProvider';
import { IInstanceConsoleOutput, IInstanceMultiOutputLog, InstanceReader } from '../../instance/InstanceReader';

// IJobManifestPodLogs is the data needed to get logs
export interface IJobManifestPodLogsProps {
  account: string;
  location: string;
  linkName: string;
  podNamesProviders: IPodNameProvider[];
}

export interface IJobManifestPodLogsState {
  containerLogs: IInstanceMultiOutputLog[];
  showModal: boolean;
  selectedContainerLog: IInstanceMultiOutputLog;
  errorMessage: string;
  loadingLogs: boolean;
}

// JobManifestPodLogs exposes pod logs for Job type manifests in the deploy manifest stage
export class JobManifestPodLogs extends React.Component<IJobManifestPodLogsProps, IJobManifestPodLogsState> {
  private ansiUp: AnsiUp;

  constructor(props: IJobManifestPodLogsProps) {
    super(props);
    this.state = {
      containerLogs: [],
      selectedContainerLog: null,
      showModal: false,
      errorMessage: null,
      loadingLogs: false,
    };
    bindAll(this, ['open', 'close', 'onClick']);
    this.ansiUp = new AnsiUp();
  }

  private canShow(): boolean {
    const { podNamesProviders } = this.props;
    return podNamesProviders.every((pod) => pod.getPodName() !== '') && !this.state.loadingLogs;
  }

  private resourceRegion(): string {
    return this.props.location;
  }

  private podName(pod?: IPodNameProvider): string {
    return `pod ${pod.getPodName()}`;
  }

  public close() {
    this.setState({ showModal: false });
  }

  public open() {
    this.setState({ showModal: true });
  }

  public onClick() {
    const { account, podNamesProviders } = this.props;
    const region = this.resourceRegion();

    this.setState({ loadingLogs: true });

    const promises = podNamesProviders.map((p) => {
      const podName = this.podName(p);
      return InstanceReader.getConsoleOutput(account, region, podName, 'kubernetes');
    });

    Promise.all(promises)
      .then((response: IInstanceConsoleOutput[]) => {
        const tempLogs = [] as IInstanceMultiOutputLog[];

        response.forEach((r) => {
          const containerLogs = r.output as IInstanceMultiOutputLog[];
          containerLogs.forEach((log: IInstanceMultiOutputLog) => {
            log.formattedOutput = DOMPurify.sanitize(this.ansiUp.ansi_to_html(log.output));
          });
          tempLogs.push(...containerLogs);
        });

        this.setState({
          containerLogs: tempLogs,
          selectedContainerLog: tempLogs[0],
          loadingLogs: false,
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
          <Modal show={showModal} onHide={this.close} dialogClassName="modal-lg modal-fullscreen flex-fill">
            <Modal.Header closeButton={true}>
              <Modal.Title>Console Output</Modal.Title>
            </Modal.Header>
            <Modal.Body className="flex-fill">
              {containerLogs.length && (
                <>
                  <ul className="tabs-basic console-output-tabs">
                    {containerLogs.map((log, i) => (
                      <li
                        key={`${log.name}-${i + 1}}`}
                        className={classNames('console-output-tab', {
                          selected: log.name === selectedContainerLog.name,
                        })}
                        onClick={() => this.selectLog(log)}
                      >
                        {`${log.name}-${i + 1}`}
                      </li>
                    ))}
                  </ul>
                  <pre
                    className="body-small fill-no-flex"
                    dangerouslySetInnerHTML={{ __html: selectedContainerLog.formattedOutput }}
                  ></pre>
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
