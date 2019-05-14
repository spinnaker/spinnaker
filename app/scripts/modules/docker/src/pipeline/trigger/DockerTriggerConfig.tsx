import * as React from 'react';

import { BaseTrigger } from '@spinnaker/core';

import { DockerImageAndTagSelector, IDockerImageAndTagChanges } from '../../image';
import { IDockerTrigger } from './IDockerTrigger';

export interface IDockerTriggerConfigProps {
  trigger: IDockerTrigger;
  triggerUpdated: (trigger: IDockerTrigger) => void;
}

export class DockerTriggerConfig extends React.Component<IDockerTriggerConfigProps> {
  constructor(props: IDockerTriggerConfigProps) {
    super(props);
  }

  private dockerChanged = (changes: IDockerImageAndTagChanges) => {
    // Trigger doesn't use imageId.
    const { imageId, ...rest } = changes;
    this.onUpdateTrigger({ ...rest });
  };

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private DockerTriggerContents = () => {
    const { trigger } = this.props;
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
      </div>
    );
  };

  public render() {
    const { DockerTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<DockerTriggerContents />} />;
  }
}
