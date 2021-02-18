import React from 'react';

import { IDockerTrigger, IExecutionTriggerStatusComponentProps, Registry } from '@spinnaker/core';

import { DockerTriggerConfig } from './DockerTriggerConfig';
import { DockerTriggerTemplate } from './DockerTriggerTemplate';

const DockerTriggerExecutionStatus = (props: IExecutionTriggerStatusComponentProps) => {
  const trigger = props.trigger as IDockerTrigger;
  return (
    <li>
      {trigger.repository}:{trigger.tag}
    </li>
  );
};

Registry.pipeline.registerTrigger({
  label: 'Docker Registry',
  description: 'Executes the pipeline on an image update',
  key: 'docker',
  component: DockerTriggerConfig,
  manualExecutionComponent: DockerTriggerTemplate,
  executionStatusComponent: DockerTriggerExecutionStatus,
  executionTriggerLabel: () => 'Docker Registry',
  validators: [
    {
      type: 'requiredField',
      fieldName: 'account',
      message: '<strong>Registry</strong> is a required field for Docker Registry triggers.',
    },
    {
      type: 'requiredField',
      fieldName: 'repository',
      message: '<strong>Image</strong> is a required field for Docker Registry triggers.',
    },
    {
      type: 'serviceAccountAccess',
      preventSave: true,
      message: `You do not have access to the service account configured in this pipeline's Docker Registry trigger.
                You will not be able to save your edits to this pipeline.`,
    },
  ],
});
