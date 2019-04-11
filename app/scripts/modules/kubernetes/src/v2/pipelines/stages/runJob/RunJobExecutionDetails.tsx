import * as React from 'react';
import { get } from 'lodash';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  AccountTag,
  ReactModal,
  ReactInjector,
  LogsModal,
  ILogsModalProps,
} from '@spinnaker/core';

export class RunJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'runJobConfig';

  public showLogsModal = (_event: any): void => {
    const { stage, execution } = this.props;
    const { executionService } = ReactInjector;
    executionService.getExecution(execution.id).then((fullExecution: any) => {
      const fullStage = fullExecution.stages.find((s: any) => s.id === stage.id);
      if (!fullStage) {
        return;
      }

      const modalProps = { dialogClassName: 'modal-lg modal-fullscreen' };
      ReactModal.show(
        LogsModal,
        {
          logs: get(fullStage, 'context.jobStatus.logs', 'No log output found.'),
        } as ILogsModalProps,
        modalProps,
      );
    });
  };

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.account} />
              </dd>
            </dl>
            {stage.context.jobStatus && stage.context.jobStatus.location && (
              <dl className="dl-narrow dl-horizontal">
                <dt>Namespace</dt>
                <dd>{stage.context.jobStatus.location}</dd>
              </dl>
            )}
          </div>
          {stage.context.jobStatus && stage.context.jobStatus.logs && (
            <div className="col-md-9">
              <dl className="dl-narrow dl-horizontal">
                <dt>Logs</dt>
                <dd>
                  <a onClick={this.showLogsModal}>Console Output (Raw)</a>
                </dd>
              </dl>
            </div>
          )}
        </div>
      </ExecutionDetailsSection>
    );
  }
}
