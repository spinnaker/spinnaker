import React from 'react';

import { CreatePipelineModal } from './CreatePipelineModal';
import { Application } from '../../application';
import { Tooltip } from '../../presentation/Tooltip';
import { ReactInjector } from '../../reactShims';
import { logger } from '../../utils';

export interface ICreatePipelineButtonProps {
  application: Application;
  asLink?: boolean;
}

export interface ICreatePipelineButtonState {
  showCreatePipelineModal: boolean;
}

export class CreatePipelineButton extends React.Component<ICreatePipelineButtonProps, ICreatePipelineButtonState> {
  constructor(props: ICreatePipelineButtonProps) {
    super(props);

    this.state = {
      showCreatePipelineModal: false,
    };
  }

  private showCallBack = (showCreatePipelineModal: boolean) => {
    this.setState({ showCreatePipelineModal });
  };

  private createPipeline = () => {
    logger.log({ category: 'Pipelines', action: 'Create Pipeline' });
    this.setState({ showCreatePipelineModal: true });
  };

  private goToPipelineConfig = (id: string) => {
    const { $state } = ReactInjector;
    if (!$state.current.name.includes('.executions.execution')) {
      $state.go('^.pipelineConfig', { pipelineId: id });
    } else {
      $state.go('^.^.pipelineConfig', { pipelineId: id });
    }
  };

  public render() {
    const modal = (
      <CreatePipelineModal
        show={this.state.showCreatePipelineModal}
        showCallback={this.showCallBack}
        pipelineSavedCallback={this.goToPipelineConfig}
        application={this.props.application}
      />
    );
    if (this.props.asLink) {
      return (
        <a className="clickable" onClick={this.createPipeline}>
          Configure a new pipeline
          {modal}
        </a>
      );
    }
    return (
      <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={this.createPipeline}>
        <span className="glyphicon glyphicon-plus-sign visible-xl-inline" />
        <Tooltip value="Create Pipeline or Strategy">
          <span className="glyphicon glyphicon-plus-sign hidden-xl-inline" />
        </Tooltip>
        <span className="visible-xl-inline"> Create</span>
        {modal}
      </button>
    );
  }
}
