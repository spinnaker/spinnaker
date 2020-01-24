import React from 'react';

import { Application, DeployInitializer, ITemplateSelectionText } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';

export interface IServerGroupTemplateSelectionProps {
  app: Application;
  command: ICloudFoundryCreateServerGroupCommand;
  onDismiss: () => void;
  onTemplateSelected: () => void;
}

export interface IServerGroupTemplateSelectionState {
  templateSelectionText: ITemplateSelectionText;
}

export class ServerGroupTemplateSelection extends React.Component<
  IServerGroupTemplateSelectionProps,
  IServerGroupTemplateSelectionState
> {
  constructor(props: IServerGroupTemplateSelectionProps) {
    super(props);
    this.state = {
      templateSelectionText: {
        copied: [
          'account, region, cluster name (stack, details)',
          'routes',
          'service instances',
          'instance settings',
          'environment variables',
        ],
        notCopied: ['artifacts'],
        additionalCopyText: '',
      },
    };
  }

  public render() {
    const { app, command, onDismiss, onTemplateSelected } = this.props;
    const { templateSelectionText } = this.state;

    return (
      <DeployInitializer
        cloudProvider="cloudfoundry"
        application={app}
        command={command}
        onDismiss={onDismiss}
        onTemplateSelected={onTemplateSelected}
        templateSelectionText={templateSelectionText}
      />
    );
  }
}
