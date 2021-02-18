import { get } from 'lodash';
import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';

export class AwsCodeBuildExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'awsCodeBuildStatus';

  public render() {
    const { current, name, stage } = this.props;
    const status: string = get(stage, 'context.buildInfo.status');
    const arn: string = get(stage, 'context.buildInfo.arn');
    const buildUrl: string = get(stage, 'context.buildInfo.buildUrl');
    const cwLogs: string = get(stage, 'context.buildInfo.logs.deepLink');
    const s3Logs: string = get(stage, 'context.buildInfo.logs.s3DeepLink');

    // S3 Logs don't exist if build is faulted or still in progress
    const hasS3Logs: boolean = s3Logs && status !== 'IN_PROGRESS' && status !== 'FAULT';

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-8">
            {status && (
              <div className="row">
                <div className="col-md-6">
                  <strong>Status:</strong>
                </div>
                <div className="col-md-6">{status}</div>
              </div>
            )}
            {arn && (
              <div className="row">
                <div className="col-md-6">
                  <strong>Build ARN:</strong>
                </div>
                <div className="col-md-6">{arn}</div>
              </div>
            )}
            {buildUrl && (
              <div className="row">
                <div className="col-md-6">
                  <strong>Build Link:</strong>
                </div>
                <a className="col-md-6" href={buildUrl} target="_blank">
                  link
                </a>
              </div>
            )}
            {cwLogs && (
              <div className="row">
                <div className="col-md-6">
                  <strong>CloudWatch Logs:</strong>
                </div>
                <a className="col-md-6" href={cwLogs} target="_blank">
                  link
                </a>
              </div>
            )}
            {hasS3Logs && (
              <div className="row">
                <div className="col-md-6">
                  <strong>S3 Logs:</strong>
                </div>
                <a className="col-md-6" href={s3Logs} target="_blank">
                  link
                </a>
              </div>
            )}
          </div>
        </div>
        {stage.failureMessage && <StageFailureMessage stage={stage} message={stage.failureMessage} />}
      </ExecutionDetailsSection>
    );
  }
}
