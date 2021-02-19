import * as React from 'react';

import { IExecutionTriggerStatusComponentProps } from 'core/domain';
import { IHelmTrigger } from 'core/domain/IHelmTrigger';

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
