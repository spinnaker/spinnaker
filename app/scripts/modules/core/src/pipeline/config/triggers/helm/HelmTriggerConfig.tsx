import React from 'react';

import { IHelmTrigger } from 'core/domain/IHelmTrigger';

import { HelmTriggerTemplate, IHelmTriggerTemplateState } from './HelmTriggerTemplate';

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
