import { IExecutionTriggerStatusComponentProps } from 'core/domain';
import { IHelmTrigger } from 'core/domain/IHelmTrigger';
import * as React from 'react';

export const HelmTriggerExecutionStatus = (props: IExecutionTriggerStatusComponentProps) => {
  const trigger = props.trigger as IHelmTrigger;
  const name = trigger.artifactName;
  const version = trigger.version;

  return (
    <li>
      {name}:{version}
    </li>
  );
};
