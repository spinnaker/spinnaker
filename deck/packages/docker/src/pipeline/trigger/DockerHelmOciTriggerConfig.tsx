import type { FormikProps } from 'formik';
import React from 'react';

import type { IDockerTrigger } from '@spinnaker/core';
import type { IDockerChartAndTagChanges } from '../../image/DockerChartAndTagSelector';
import { DockerChartAndTagSelector } from '../../image/DockerChartAndTagSelector';

export interface IDockerTriggerConfigProps {
  formik: FormikProps<IDockerTrigger>;
  triggerUpdated: (trigger: IDockerTrigger) => void;
}

export function DockerHelmOciTriggerConfig(props: IDockerTriggerConfigProps) {
  const { formik } = props;
  const trigger = formik.values;

  const dockerChanged = (changes: IDockerChartAndTagChanges) => {
    // Trigger doesn't use imageId.
    const { imageId, ...rest } = changes;
    props.triggerUpdated(rest as IDockerTrigger);
  };

  return (
    <div className="form-horizontal">
      <DockerChartAndTagSelector
        allowManualDefinition={false}
        specifyTagByRegex={true}
        account={trigger.account}
        organization={trigger.organization}
        registry={trigger.registry}
        repository={trigger.repository}
        tag={trigger.tag}
        showRegistry={true}
        onChange={dockerChanged}
        showDigest={false}
      />
    </div>
  );
}
