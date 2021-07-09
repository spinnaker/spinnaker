import React from 'react';

import { IPipelineCommand } from '../../domain';

export interface ITriggerTemplateComponentProps {
  command: IPipelineCommand;
  updateCommand: (path: string, value: any) => void;
}

export interface ITriggerTemplateProps extends ITriggerTemplateComponentProps {
  component: React.ComponentType<ITriggerTemplateComponentProps>;
}

export class TriggerTemplate extends React.Component<ITriggerTemplateProps> {
  public render(): React.ReactElement<TriggerTemplate> {
    const { component: Component, updateCommand, command } = this.props;

    return <Component updateCommand={updateCommand} command={command} />;
  }
}
