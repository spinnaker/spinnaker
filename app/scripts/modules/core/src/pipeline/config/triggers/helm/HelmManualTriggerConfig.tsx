import { $q } from 'ngimport';
import React from 'react';

import { HelmTriggerTemplate, IHelmTriggerTemplateState } from './HelmTriggerTemplate';
import { IHelmTrigger } from '../../../../domain/IHelmTrigger';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';

const HelmManualTriggerConfig = (props: ITriggerTemplateComponentProps) => {
  const onHelmChanged = (changes: IHelmTriggerTemplateState) => {
    const { command, updateCommand } = props;
    updateCommand('extraFields', command.extraFields || {});
    updateCommand('extraFields.chart', changes.chart);
    updateCommand('extraFields.version', changes.version);
    updateCommand('extraFields.artifacts', [
      {
        type: 'helm/chart',
        name: changes.chart,
        version: changes.version,
        reference: changes.account,
      },
    ]);

    updateCommand('triggerInvalid', false);
  };

  return (
    <HelmTriggerTemplate
      isManual={true}
      trigger={props.command.trigger as IHelmTrigger}
      onHelmChanged={onHelmChanged}
    />
  );
};

HelmManualTriggerConfig.formatLabel = (trigger: IHelmTrigger): PromiseLike<string> => {
  return $q.when(`(Helm) ${trigger.account ? trigger.account + ':' : ''}${trigger.chart || ''}`);
};

export { HelmManualTriggerConfig };
