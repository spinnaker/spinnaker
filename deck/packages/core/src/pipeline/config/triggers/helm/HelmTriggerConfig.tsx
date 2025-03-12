import React from 'react';

import type { IHelmTriggerTemplateState } from './HelmTriggerTemplate';
import { HelmTriggerTemplate } from './HelmTriggerTemplate';
import type { IHelmTrigger } from '../../../../domain/IHelmTrigger';

export interface IHelmConfigProps {
  trigger: IHelmTrigger;
  triggerUpdated: (trigger: IHelmTrigger) => void;
}

export const HelmTriggerConfig = (props: IHelmConfigProps) => {
  const onHelmChanged = (changes: IHelmTriggerTemplateState) => {
    props.triggerUpdated && props.triggerUpdated({ ...props.trigger, ...changes });
  };

  return <HelmTriggerTemplate isManual={false} trigger={props.trigger} onHelmChanged={onHelmChanged} />;
};
