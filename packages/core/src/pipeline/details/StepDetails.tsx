import React from 'react';

import { Application } from '../../application';
import { StepExecutionDetails } from '../config/stages/common/StepExecutionDetails';
import { IExecution, IExecutionDetailsSection, IExecutionStage, IStageTypeConfig } from '../../domain';
import { robotToHuman } from '../../presentation/robotToHumanFilter/robotToHuman.filter';
import { NgReact } from '../../reactShims';
import { StatusGlyph } from '../../task/StatusGlyph';

export interface IStepDetailsProps {
  application: Application;
  config: IStageTypeConfig;
  execution: IExecution;
  stage: IExecutionStage;
}

export interface IStepDetailsSections {
  configSections?: string[];
  executionDetailsSections?: IExecutionDetailsSection[];
  provider: string;
  sourceUrl?: string;
}

export class StepDetails extends React.Component<IStepDetailsProps> {
  constructor(props: IStepDetailsProps) {
    super(props);
  }

  private deriveSectionsFromProps(): IStepDetailsSections {
    let configSections: string[] = [];
    let sourceUrl: string;
    let executionDetailsSections: IExecutionDetailsSection[];
    let provider: string;

    const stageConfig = this.props.config;
    if (stageConfig) {
      if (stageConfig.executionConfigSections) {
        configSections = stageConfig.executionConfigSections;
      }
      if (stageConfig.executionDetailsSections) {
        // React execution details
        executionDetailsSections = stageConfig.executionDetailsSections;
      } else {
        // Angular execution details
        sourceUrl = stageConfig.executionDetailsUrl || require('./defaultExecutionDetails.html');
      }
      provider = stageConfig.cloudProvider;
    }
    return { configSections, executionDetailsSections, provider, sourceUrl };
  }

  public render(): React.ReactElement<StepDetails> {
    const { application, config, execution, stage } = this.props;
    const { executionDetailsSections, provider, sourceUrl, configSections } = this.deriveSectionsFromProps();
    const { StepExecutionDetailsWrapper } = NgReact;
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
        {sourceUrl && (
          <StepExecutionDetailsWrapper {...detailsProps} sourceUrl={sourceUrl} configSections={configSections} />
        )}
        {executionDetailsSections && (
          <StepExecutionDetails {...detailsProps} detailsSections={executionDetailsSections} />
        )}
      </div>
    );
  }
}
