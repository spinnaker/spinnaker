import { find, get } from 'lodash';
import React from 'react';

import { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common/ExecutionDetailsSection';
import { StageFailureMessage } from '../../../details/StageFailureMessage';
import { IStage } from '../../../../domain';

export interface IApplySourceServerGroupCapacityDetailsState {
  parentDeployStage: IStage;
}

export class ApplySourceServerGroupCapacityDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IApplySourceServerGroupCapacityDetailsState
> {
  public static title = 'capacitySnapshot';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = { parentDeployStage: find(props.execution.stages, (stage) => stage.id === props.stage.parentStageId) };
  }

  public render() {
    const { stage, current, name } = this.props;
    const { parentDeployStage } = this.state;
    const snapshot = get(parentDeployStage, 'context.sourceServerGroupCapacitySnapshot', {} as IStage);
    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12">
            <dl className="dl-narrow dl-horizontal">
              <dt>Server Group</dt>
              {stage.context.serverGroupName && <dd>{stage.context.serverGroupName}</dd>}
              {!stage.context.serverGroupName && <dd>Unknown</dd>}
              <dt>Min</dt>
              <dd>{snapshot.min}</dd>
              <dt>Desired</dt>
              <dd>{snapshot.desired}</dd>
              <dt>Max</dt>
              <dd>{snapshot.max}</dd>
            </dl>
          </div>
        </div>

        <StageFailureMessage stage={stage} message={stage.failureMessage} />
      </ExecutionDetailsSection>
    );
  }
}
