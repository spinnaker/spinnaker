import * as React from 'react';

import { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { ExecutionDetailsSection } from '@spinnaker/core';
import { EvaluateCloudFormationChangeSetExecutionApproval } from './evaluateCloudFormationChangeSetExecutionApproval';

export class EvaluateCloudFormationChangeSetExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'Change Set Execution';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
  }

  public render() {
    const { application, execution, stage, current, name } = this.props;
    const hasReplacement = stage.context.changeSetContainsReplacement;
    const askAction = hasReplacement && stage.isRunning && stage.context.actionOnReplacement === 'ask';
    const isChangeSet = stage.context.isChangeSet;

    return (
      <ExecutionDetailsSection name={name} current={current}>
        {askAction ? (
          <EvaluateCloudFormationChangeSetExecutionApproval
            key={stage.refId}
            application={application}
            execution={execution}
            stage={stage}
          />
        ) : (
          <div>
            {isChangeSet ? (
              <div>
                <dl className="no-margin">
                  <dt>ChangeSet Name</dt>
                  <dd>{stage.context.changeSetName}</dd>
                  <dt>Replacement</dt>
                  <dd>{String(hasReplacement)}</dd>
                  {stage.context.changeSetExecutionChoice && (
                    <div>
                      <dt>Judgment</dt>
                      <dd>{stage.context.changeSetExecutionChoice}</dd>
                      <dt>Judged By</dt>
                      <dd>{stage.context.lastModifiedBy}</dd>
                    </div>
                  )}
                </dl>
              </div>
            ) : (
              <div>No changeSets found</div>
            )}
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
