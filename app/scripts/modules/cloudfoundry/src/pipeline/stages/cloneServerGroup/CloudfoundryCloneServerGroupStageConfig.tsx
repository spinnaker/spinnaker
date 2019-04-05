import * as React from 'react';

import { IPipeline, IStageConfigProps, StageConstants } from '@spinnaker/core';

import { CloudFoundryCreateServerGroupModal } from 'cloudfoundry/serverGroup/configure/wizard/CreateServerGroupModal';
import { CloudFoundryReactInjector } from 'cloudfoundry/reactShims';

export interface ICloudfoundryCloneServerGroupStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryCloneServerGroupStageConfigState {
  buttonText: string;
}

export class CloudfoundryCloneServerGroupStageConfig extends React.Component<
  ICloudfoundryCloneServerGroupStageProps,
  ICloudfoundryCloneServerGroupStageConfigState
> {
  constructor(props: ICloudfoundryCloneServerGroupStageProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    props.stage.application = props.application.name;
    this.state = {
      buttonText: props.stage.destination ? 'Edit clone configuration' : 'Add clone configuration',
    };
  }

  private handleResult = (command: any) => {
    this.props.stage.credentials = command.credentials;
    this.props.stage.capacity = command.capacity;
    this.props.stage.account = command.account;
    this.props.stage.destination = command.destination;
    this.props.stage.delayBeforeDisableSec = command.delayBeforeDisableSec;
    this.props.stage.freeFormDetails = command.freeFormDetails;
    this.props.stage.maxRemainingAsgs = command.maxRemainingAsgs;
    this.props.stage.region = command.region;
    this.props.stage.startApplication = command.startApplication;
    this.props.stage.stack = command.stack;
    this.props.stage.strategy = command.strategy;
    this.props.stage.target = command.target;
    this.props.stage.targetCluster = command.targetCluster;
    this.props.stage.manifest = command.manifest;
    this.setState({ buttonText: 'Edit clone configuration' });
    this.props.stageFieldUpdated();
  };

  private addCluster = () => {
    const { application, stage, pipeline } = this.props;
    const title = 'Clone Cluster';
    CloudFoundryReactInjector.cfServerGroupCommandBuilder
      .buildCloneServerGroupCommandFromPipeline(stage, pipeline)
      .then((command: any) => {
        return CloudFoundryCreateServerGroupModal.show({
          application,
          command,
          isSourceConstant: false,
          title,
        });
      })
      .then(this.handleResult)
      .catch(() => {});
  };

  public render() {
    const { stage } = this.props;
    const { buttonText } = this.state;
    const cloneTargets = StageConstants.TARGET_LIST;
    return (
      <div className="form-horizontal">
        <div>
          <h4>Clone Source</h4>
          <table className="table table-condensed table-deployStage">
            <thead>
              <tr>
                <th>Account</th>
                <th>Region</th>
                <th>Cluster</th>
                <th>Target</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>{stage.credentials}</td>
                <td>{stage.region}</td>
                <td>{stage.targetCluster}</td>
                <td>{cloneTargets.filter(t => t.val === stage.target).map(t => t.label)}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div>
          <h4>Clone Destination</h4>
          <table className="table table-condensed table-deployStage">
            <thead>
              <tr>
                <th>Account</th>
                <th>Region</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>{stage.account || ''}</td>
                <td>{stage.destination ? stage.destination.region : ''}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <button className="btn btn-block btn-sm add-new" onClick={() => this.addCluster()}>
          <span className="glyphicon glyphicon-plus-sign" /> {buttonText}
        </button>
      </div>
    );
  }
}
