import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { IExecution, IExecutionDetailsSection, IExecutionStage, IStageTypeConfig } from 'core/domain';
import { NgReact } from 'core/reactShims';
import { StageExecutionDetails } from 'core/pipeline/config/stages/core/StageExecutionDetails';
import { StatusGlyph } from 'core/task/StatusGlyph';
import { robotToHuman } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';

export interface IStageDetailsProps {
  application: Application;
  config: IStageTypeConfig;
  execution: IExecution;
  stage: IExecutionStage;
}

export interface IStageDetailsState {
  configSections?: string[];
  executionDetailsSections?: IExecutionDetailsSection[];
  provider: string;
  sourceUrl?: string;
}

@BindAll()
export class StageDetails extends React.Component<IStageDetailsProps, IStageDetailsState> {
  constructor(props: IStageDetailsProps) {
    super(props);
    this.state = this.getState();
  }

  private getState(): IStageDetailsState {
    let configSections: string[] = [];
    let sourceUrl: string;
    let executionDetailsSections: IExecutionDetailsSection[];

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
    }
    return { configSections, executionDetailsSections, provider: stageConfig.cloudProvider, sourceUrl };
  }

  public componentWillReceiveProps() {
    this.setState(this.getState());
  }

  public render(): React.ReactElement<StageDetails> {
    const { application, execution, stage } = this.props;
    const { executionDetailsSections, provider, sourceUrl, configSections } = this.state;
    const { StageDetailsWrapper } = NgReact;
    const detailsProps = { application, execution, provider, stage };

    return (
      <div className="stage-details">
        <div className="stage-details-heading">
          <h5>
            <StatusGlyph item={stage} />
            {robotToHuman(stage.name || stage.type)}
          </h5>
        </div>
        {sourceUrl && <StageDetailsWrapper {...detailsProps} sourceUrl={sourceUrl} configSections={configSections} />}
        {executionDetailsSections && <StageExecutionDetails {...detailsProps} detailsSections={executionDetailsSections} />}
      </div>
    );
  }
}
