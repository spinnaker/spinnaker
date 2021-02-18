import { FormikProps } from 'formik';
import React from 'react';

import { IDockerTrigger } from '@spinnaker/core';

import { DockerImageAndTagSelector, IDockerImageAndTagChanges } from '../../image';

export interface IDockerTriggerConfigProps {
  formik: FormikProps<IDockerTrigger>;
  triggerUpdated: (trigger: IDockerTrigger) => void;
}

export function DockerTriggerConfig(props: IDockerTriggerConfigProps) {
  const { formik } = props;
  const trigger = formik.values;

  const dockerChanged = (changes: IDockerImageAndTagChanges) => {
    // Trigger doesn't use imageId.
    const { imageId, ...rest } = changes;
    props.triggerUpdated(rest as IDockerTrigger);
  };

  return (
    <div className="form-horizontal">
      <DockerImageAndTagSelector
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
