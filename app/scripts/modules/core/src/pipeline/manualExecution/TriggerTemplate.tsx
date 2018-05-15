import * as React from 'react';

import { IPipelineCommand } from 'core/domain';

export interface ITriggerTemplateComponentProps {
  command: IPipelineCommand;
}

export interface ITriggerTemplateProps extends ITriggerTemplateComponentProps {
  component: React.ComponentType<ITriggerTemplateComponentProps>;
}

export class TriggerTemplate extends React.Component<ITriggerTemplateProps> {
  public render(): React.ReactElement<TriggerTemplate> {
    const { component: Component, command } = this.props;

    return <Component command={command} />;
  }
}
