import React from 'react';

import { ManualJudgmentApproval } from './ManualJudgmentApproval';
import { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';
import { IStage } from '../../../../domain';
import { robotToHuman } from '../../../../presentation/robotToHumanFilter/robotToHuman.filter';
import { timestamp } from '../../../../utils/timeFormatters';

export interface IManualJudgmentExecutionDetailsState {
  parentDeployStage: IStage;
}

export class ManualJudgmentExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IManualJudgmentExecutionDetailsState
> {
  public static title = 'manualJudgment';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
  }

  public render() {
    const { application, execution, stage, current, name } = this.props;
    const hasJudgment = stage.context.judgmentStatus || ['SKIPPED', 'SUCCEEDED'].includes(stage.status);
    return (
      <ExecutionDetailsSection name={name} current={current}>
        <dl className="no-margin">
          {hasJudgment && <dt key="title">Judgment</dt>}
          {hasJudgment && <dd key="value">{robotToHuman(stage.context.judgmentStatus || 'No judgment made')}</dd>}
          {stage.status === 'SKIPPED' && <dd>Skipped</dd>}
          {stage.context.lastModifiedBy && <dt>Judged By</dt>}
          {stage.context.lastModifiedBy && (
            <dd>
              <span>{stage.context.lastModifiedBy}</span>
              {stage.context.propagateAuthenticationContext && (
                <span>
                  {' '}
                  (<em>authentication propagated</em>)
                </span>
              )}
              <br />
              {timestamp(stage.endTime)}
            </dd>
          )}

          {stage.context.judgmentInput && <dt>Input</dt>}
          {stage.context.judgmentInput && <dd>{robotToHuman(stage.context.judgmentInput)}</dd>}
        </dl>
        <ManualJudgmentApproval key={stage.refId} application={application} execution={execution} stage={stage} />
        {stage.context.judgmentInput && <StageFailureMessage stage={stage} message={stage.failureMessage} />}
      </ExecutionDetailsSection>
    );
  }
}
