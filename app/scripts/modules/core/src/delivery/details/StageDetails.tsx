import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { IExecution, IExecutionStage, IStageTypeConfig } from 'core/domain';
import { NgReact } from 'core/reactShims';
import { StatusGlyph } from 'core/task/StatusGlyph';
import { robotToHuman } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';

export interface IStageDetailsProps {
  application: Application;
  config: IStageTypeConfig;
  execution: IExecution;
  stage: IExecutionStage;
}

export interface IStageDetailsState {
  sourceUrl?: string;
  configSections?: string[];
}

@BindAll()
export class StageDetails extends React.Component<IStageDetailsProps, IStageDetailsState> {
  constructor(props: IStageDetailsProps) {
    super(props);
    this.state = this.getState();
  }

  private getState(): IStageDetailsState {
    let configSections: string[] = [];
    let sourceUrl = require('./defaultExecutionDetails.html');

    const stageConfig = this.props.config;
    if (stageConfig) {
      if (stageConfig.executionConfigSections) {
        configSections = stageConfig.executionConfigSections;
      }
      if (stageConfig.executionDetailsUrl) {
        sourceUrl = stageConfig.executionDetailsUrl;
      }
    }
    return { configSections, sourceUrl };
  }

  public componentWillReceiveProps() {
    this.setState(this.getState());
  }

  public render(): React.ReactElement<StageDetails> {
    const { application, execution, stage } = this.props;
    const { sourceUrl, configSections } = this.state;

    const { StageDetailsWrapper } = NgReact;

    if ( sourceUrl ) {
      return (
        <div className="stage-details">
          <div className="stage-details-heading">
            <h5>
              <StatusGlyph item={stage} />
              {robotToHuman(stage.name || stage.type)}
            </h5>
          </div>
          <StageDetailsWrapper application={application} execution={execution} sourceUrl={sourceUrl} configSections={configSections} stage={stage} />
        </div>
      );
    }
    return null;
  }
}
