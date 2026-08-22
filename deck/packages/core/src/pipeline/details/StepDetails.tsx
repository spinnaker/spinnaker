import React from 'react';

import { StepExecutionDetailsWrapper } from './StepExecutionDetailsWrapper';
import type { Application } from '../../application';
import { StepExecutionDetails } from '../config/stages/common/StepExecutionDetails';
import type { IExecution, IExecutionDetailsSection, IExecutionStage, IStageTypeConfig } from '../../domain';
import { robotToHuman } from '../../presentation/robotToHumanFilter/robotToHuman.filter';
import { StatusGlyph } from '../../task/StatusGlyph';

export interface IStepDetailsProps {
  application: Application;
  config: IStageTypeConfig;
  execution: IExecution;
  stage: IExecutionStage;
}

export interface IStepDetailsSections {
  executionDetailsSections?: IExecutionDetailsSection[];
  provider: string;
}

export class StepDetails extends React.Component<IStepDetailsProps> {
  constructor(props: IStepDetailsProps) {
    super(props);
  }

  private deriveSectionsFromProps(): IStepDetailsSections {
    let executionDetailsSections: IExecutionDetailsSection[];
    let provider: string;

    const stageConfig = this.props.config;
    if (stageConfig) {
      if (stageConfig.executionDetailsSections) {
        executionDetailsSections = stageConfig.executionDetailsSections;
      }
      provider = stageConfig.cloudProvider;
    }
    return { executionDetailsSections, provider };
  }

  public render(): React.ReactElement<StepDetails> {
    const { application, config, execution, stage } = this.props;
    const { executionDetailsSections, provider } = this.deriveSectionsFromProps();
    const detailsProps = { application, config, execution, provider, stage };

    return (
      <div className="stage-details">
        <div className="stage-details-heading">
          {stage && (
            <h5>
              <StatusGlyph item={stage} />
              {robotToHuman(stage.name || stage.type)}
            </h5>
          )}
        </div>
        {config && !executionDetailsSections && <StepExecutionDetailsWrapper {...detailsProps} />}
        {executionDetailsSections && (
          <StepExecutionDetails {...detailsProps} detailsSections={executionDetailsSections} />
        )}
      </div>
    );
  }
}
