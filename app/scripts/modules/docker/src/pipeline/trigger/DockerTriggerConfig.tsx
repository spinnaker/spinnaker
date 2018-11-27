import * as React from 'react';

import { ITriggerConfigProps, RunAsUser, SETTINGS, ServiceAccountReader, IDockerTrigger } from '@spinnaker/core';

import { DockerImageAndTagSelector, IDockerImageAndTagChanges } from '../../image/DockerImageAndTagSelector';

export interface IDockerTriggerConfigProps extends ITriggerConfigProps {
  trigger: IDockerTrigger;
}

export interface IDockerTriggerConfigState {
  fiatEnabled: boolean;
  serviceAccounts: string[];
}

export class DockerTriggerConfig extends React.Component<IDockerTriggerConfigProps, IDockerTriggerConfigState> {
  public state: IDockerTriggerConfigState = {
    fiatEnabled: SETTINGS.feature.fiatEnabled,
    serviceAccounts: [],
  };

  public componentDidMount() {
    ServiceAccountReader.getServiceAccounts().then((serviceAccounts: string[]) => {
      this.setState({ serviceAccounts });
    });
  }

  private dockerChanged = (changes: IDockerImageAndTagChanges) => {
    // Trigger doesn't use imageId.
    const { imageId, ...rest } = changes;
    Object.assign(this.props.trigger, rest);
    this.props.fieldUpdated();
    this.setState({});
  };

  private runAsUserChanged = (user: string) => {
    this.props.trigger.runAsUser = user !== '' ? user : null;
    this.props.fieldUpdated();
    this.setState({});
  };

  public render() {
    const { trigger } = this.props;
    const { fiatEnabled, serviceAccounts } = this.state;
    return (
      <div className="form-horizontal">
        <DockerImageAndTagSelector
          specifyTagByRegex={true}
          account={trigger.account}
          organization={trigger.organization}
          registry={trigger.registry}
          repository={trigger.repository}
          tag={trigger.tag}
          showRegistry={true}
          onChange={this.dockerChanged}
          showDigest={false}
        />

        {fiatEnabled && (
          <div className="form-group">
            <RunAsUser
              serviceAccounts={serviceAccounts}
              value={trigger.runAsUser}
              onChange={this.runAsUserChanged}
              selectClasses=""
              selectColumns={8}
            />
          </div>
        )}
      </div>
    );
  }
}
