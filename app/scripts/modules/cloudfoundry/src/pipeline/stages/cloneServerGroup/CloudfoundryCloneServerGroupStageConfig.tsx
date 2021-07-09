import React from 'react';

import { IStageConfigProps, StageConstants } from '@spinnaker/core';
import { CloudFoundryServerGroupCommandBuilder } from '../../../serverGroup/configure';
import { CloudFoundryCreateServerGroupModal } from '../../../serverGroup/configure/wizard/CreateServerGroupModal';

export interface ICloudfoundryCloneServerGroupStageConfigState {
  buttonText: string;
}

export class CloudfoundryCloneServerGroupStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryCloneServerGroupStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    this.props.updateStageField({
      cloudProvider: 'cloudfoundry',
      application: props.application.name,
    });
    this.state = {
      buttonText: props.stage.destination ? 'Edit clone configuration' : 'Add clone configuration',
    };
  }

  private handleResult = (command: any) => {
    this.props.updateStageField({
      credentials: command.credentials,
      capacity: command.capacity,
      account: command.account,
      delayBeforeDisableSec: command.delayBeforeDisableSec,
      freeFormDetails: command.freeFormDetails,
      maxRemainingAsgs: command.maxRemainingAsgs,
      region: command.region,
      startApplication: command.startApplication,
      stack: command.stack,
      strategy: command.strategy,
      target: command.target,
      targetCluster: command.targetCluster,
      manifest: command.manifest,
      source: command.source,
    });
    this.setState({ buttonText: 'Edit clone configuration' });
  };

  private addCluster = () => {
    const { application, stage, pipeline } = this.props;
    const title = 'Clone Cluster';
    const command = CloudFoundryServerGroupCommandBuilder.buildCloneServerGroupCommandFromPipeline(stage, pipeline);
    CloudFoundryCreateServerGroupModal.show({
      application,
      command,
      isSourceConstant: false,
      title,
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
                <td>{stage.source ? stage.source.account : ''}</td>
                <td>{stage.source ? stage.source.region : ''}</td>
                <td>{stage.source ? stage.source.clusterName : ''}</td>
                <td>
                  {stage.source ? cloneTargets.filter((t) => t.val === stage.source.target).map((t) => t.label) : ''}
                </td>
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
                <td>{stage.credentials}</td>
                <td>{stage.region}</td>
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
