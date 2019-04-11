import * as React from 'react';
import { get } from 'lodash';

import { IStage } from 'core/domain';
import { ReactModal } from 'core/presentation';

import { LogsModal, ILogsModalProps } from './LogsModal';

export interface IStageExecutionLogsProps {
  stage: IStage;
}

export class StageExecutionLogs extends React.Component<IStageExecutionLogsProps> {
  public isExternalLink = false;

  constructor(props: any) {
    super(props);
    // titus jobs present a link to an external system for logs
    // kubernetes jobs store logs in the execution context (for better, or for worse)
    this.isExternalLink = get<string>(this.props.stage, 'context.execution.logs') !== undefined;
  }

  public showModal = () => {
    ReactModal.show(
      LogsModal,
      {
        logs: get<string>(this.props.stage, 'context.jobStatus.logs'),
      } as ILogsModalProps,
      { dialogClassName: 'modal-lg modal-fullscreen' },
    );
  };

  public render() {
    const logs = get<string>(this.props.stage, 'context.execution.logs');
    return (
      <div className="row">
        <div className="col-md-12">
          <div className="well alert alert-info">
            {this.isExternalLink && (
              <a target="_blank" href={logs}>
                View Execution Logs
              </a>
            )}
            {!this.isExternalLink && <a onClick={this.showModal}>View Execution Logs</a>}
          </div>
        </div>
      </div>
    );
  }
}
