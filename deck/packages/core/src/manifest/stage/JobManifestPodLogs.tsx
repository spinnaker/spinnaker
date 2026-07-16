import AnsiUp from 'ansi_up';
import classNames from 'classnames';
import DOMPurify from 'dompurify';
import { bindAll } from 'lodash';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import type { IPodNameProvider } from '../PodNameProvider';
import type { IInstanceConsoleOutput, IInstanceMultiOutputLog } from '../../instance/InstanceReader';
import { InstanceReader } from '../../instance/InstanceReader';
import { SETTINGS } from '../../config/settings';

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
  autoRefresh: boolean;
}

// JobManifestPodLogs exposes pod logs for Job type manifests in the deploy manifest stage
export class JobManifestPodLogs extends React.Component<IJobManifestPodLogsProps, IJobManifestPodLogsState> {
  private ansiUp: AnsiUp;
  private autoRefreshTimer: ReturnType<typeof setInterval> | null = null;

  constructor(props: IJobManifestPodLogsProps) {
    super(props);
    this.state = {
      containerLogs: [],
      selectedContainerLog: null,
      showModal: false,
      errorMessage: null,
      loadingLogs: false,
      autoRefresh: false,
    };
    bindAll(this, ['open', 'close', 'onClick', 'refresh', 'toggleAutoRefresh']);
    this.ansiUp = new AnsiUp();
  }

  public componentWillUnmount() {
    this.clearAutoRefreshTimer();
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
    this.clearAutoRefreshTimer();
    this.setState({ showModal: false, autoRefresh: false });
  }

  public open() {
    this.setState({ showModal: true });
  }

  private clearAutoRefreshTimer() {
    if (this.autoRefreshTimer !== null) {
      clearInterval(this.autoRefreshTimer);
      this.autoRefreshTimer = null;
    }
  }

  public toggleAutoRefresh() {
    const { autoRefresh } = this.state;
    if (autoRefresh) {
      this.clearAutoRefreshTimer();
      this.setState({ autoRefresh: false });
    } else {
      const interval = SETTINGS.consoleLogRefreshIntervalMs ?? 30000;
      this.autoRefreshTimer = setInterval(this.refresh, interval);
      this.setState({ autoRefresh: true });
    }
  }

  public refresh() {
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

        this.setState((prevState) => ({
          containerLogs: tempLogs,
          selectedContainerLog:
            prevState.selectedContainerLog
              ? (tempLogs.find((l) => l.name === prevState.selectedContainerLog.name) ?? tempLogs[0])
              : tempLogs[0],
          loadingLogs: false,
          errorMessage: null,
        }));
      })
      .catch((exception: any) => {
        this.setState({ errorMessage: exception.data.message, loadingLogs: false });
      });
  }

  public onClick() {
    this.refresh();
    this.open();
  }

  public selectLog(log: IInstanceMultiOutputLog) {
    this.setState({ selectedContainerLog: log });
  }

  public render() {
    const { showModal, containerLogs, errorMessage, selectedContainerLog, loadingLogs, autoRefresh } = this.state;
    const refreshInterval = SETTINGS.consoleLogRefreshIntervalMs ?? 30000;
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
              {loadingLogs && (
                <div className="horizontal center middle" style={{ padding: '10px' }}>
                  <span className="glyphicon glyphicon-refresh glyphicon-spin" /> Loading logs...
                </div>
              )}
              {!loadingLogs && containerLogs.length > 0 && (
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
              <Button onClick={this.refresh} disabled={loadingLogs}>
                Refresh
              </Button>
              <Button
                onClick={this.toggleAutoRefresh}
                title={`Auto-refresh every ${refreshInterval / 1000}s`}
                bsStyle={autoRefresh ? 'primary' : 'default'}
              >
                {autoRefresh ? 'Auto-Refresh: On' : 'Auto-Refresh: Off'}
              </Button>
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
