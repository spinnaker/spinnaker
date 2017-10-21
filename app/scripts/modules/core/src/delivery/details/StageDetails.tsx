import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { IExecution, IExecutionDetailsComponentProps, IExecutionStage, IStageTypeConfig } from 'core/domain';
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
  configSections?: string[];
  ReactComponent?: React.ComponentClass<IExecutionDetailsComponentProps>;
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
    let ReactComponent: React.ComponentClass<IExecutionDetailsComponentProps>;

    const stageConfig = this.props.config;
    if (stageConfig) {
      if (stageConfig.executionConfigSections) {
        configSections = stageConfig.executionConfigSections;
      }
      if (stageConfig.executionDetailsComponent) {
        // React execution details
        ReactComponent = stageConfig.executionDetailsComponent;
      } else {
        // Angular execution details
        sourceUrl = stageConfig.executionDetailsUrl || require('./defaultExecutionDetails.html');
      }
    }
    return { configSections, ReactComponent, sourceUrl };
  }

  public componentWillReceiveProps() {
    this.setState(this.getState());
  }

  public render(): React.ReactElement<StageDetails> {
    const { application, execution, stage } = this.props;
    const { ReactComponent, sourceUrl, configSections } = this.state;
    const { StageDetailsWrapper } = NgReact;
    const detailsProps = { application, execution, stage, configSections };

    return (
      <div className="stage-details">
        <div className="stage-details-heading">
          <h5>
            <StatusGlyph item={stage} />
            {robotToHuman(stage.name || stage.type)}
          </h5>
        </div>
        {sourceUrl && <StageDetailsWrapper {...detailsProps} sourceUrl={sourceUrl} />}
        {ReactComponent && <ReactComponent {...detailsProps} />}
      </div>
    );
  }
}
