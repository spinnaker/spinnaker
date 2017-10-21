import * as React from 'react';

import { IExecutionDetailsComponentProps } from 'core/domain';
import { ExecutionDetailsSectionNav, StageExecutionLogs, StageFailureMessage } from 'core/delivery/details';
import { ExecutionStepDetails } from 'core/pipeline/config/stages/core/ExecutionStepDetails';
import { SkipWait } from './SkipWait';
import { stageExecutionDetails } from 'core/delivery/details';

class ExecutionDetails extends React.Component<IExecutionDetailsComponentProps> {
  public render() {
    const { application, configSections, detailsSection, execution, stage } = this.props;
    return (
      <div>
        <ExecutionDetailsSectionNav sections={configSections} />
        {detailsSection === 'waitConfig' && (
          <div className="step-section-details">
            <SkipWait application={application} execution={execution} stage={stage} />
            <StageFailureMessage stage={stage} message={stage.failureMessage} />
            <StageExecutionLogs stage={stage} />
          </div>
        )}

        {detailsSection === 'taskStatus' && (
          <div className="step-section-details">
            <div className="row">
              <ExecutionStepDetails item={stage} />
            </div>
          </div>
        )}
      </div>
    );
  }
}

export const WaitExecutionDetails = stageExecutionDetails(ExecutionDetails);
