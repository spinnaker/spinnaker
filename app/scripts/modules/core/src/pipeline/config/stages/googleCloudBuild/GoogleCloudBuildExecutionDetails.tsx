import { capitalize, get } from 'lodash';
import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export class GoogleCloudBuildExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'googleCloudBuildStatus';

  public render() {
    const { current, name, stage } = this.props;
    const logUrl: string = get(stage, 'context.buildInfo.logUrl');
    const status: string = get(stage, 'context.buildInfo.status');

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-6">
            {status && (
              <div className="row">
                <div className="col-md-6">
                  <strong>Status:</strong>
                </div>
                <div className="col-md-6">{capitalize(status)}</div>
              </div>
            )}
            {logUrl && (
              <div className="row">
                <div className="col-md-6">
                  <strong>Details:</strong>
                </div>
                <div className="col-md-6">
                  <a href={logUrl} target="_blank">
                    Build Logs
                  </a>
                </div>
              </div>
            )}
          </div>
        </div>
      </ExecutionDetailsSection>
    );
  }
}
