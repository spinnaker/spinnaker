import React from 'react';

import {
  Application,
  DeployInitializer,
  FirewallLabels,
  IServerGroupCommand,
  ITemplateSelectionText,
} from '@spinnaker/core';

export interface IServerGroupTemplateSelectionProps {
  app: Application;
  command: IServerGroupCommand;
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
          'account, region, subnet, cluster name (stack, details)',
          'load balancers',
          FirewallLabels.get('firewalls'),
          'instance type',
          'all fields on the Advanced Settings page',
        ],
        notCopied: ['the following suspended scaling processes: Launch, Terminate, AddToLoadBalancer'],
        additionalCopyText:
          'If a server group exists in this cluster at the time of deployment, its scaling policies will be copied over to the new server group.',
      },
    };

    if (!props.command.viewState.disableStrategySelection) {
      this.state.templateSelectionText.notCopied.push(
        'the deployment strategy (if any) used to deploy the most recent server group',
      );
    }
  }

  public render() {
    const { app, command, onDismiss, onTemplateSelected } = this.props;
    const { templateSelectionText } = this.state;

    return (
      <DeployInitializer
        cloudProvider="aws"
        application={app}
        command={command}
        onDismiss={onDismiss}
        onTemplateSelected={onTemplateSelected}
        templateSelectionText={templateSelectionText}
      />
    );
  }
}
