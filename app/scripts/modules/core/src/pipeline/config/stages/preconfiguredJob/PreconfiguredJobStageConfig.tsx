import React from 'react';

import { FormikFormField, TextInput } from 'core/presentation';

import { FormikStageConfig } from '../FormikStageConfig';
import { IStageConfigProps } from '../common';
import { HelpField } from '../../../../help';
import { IPreconfiguredJobParameter } from './preconfiguredJob.reader';

export function PreconfiguredJobStageConfig(props: IStageConfigProps) {
  const parameters: IPreconfiguredJobParameter[] = props.configuration.parameters ?? [];
  return (
    <FormikStageConfig
      {...props}
      onChange={props.updateStage}
      render={() =>
        parameters.map((param) => (
          <FormikFormField
            key={param.name}
            name={`parameters.${param.name}`}
            label={param.label}
            help={param.description ? <HelpField content={param.description} /> : null}
            input={(props) => <TextInput {...props} />}
            {...props}
          />
        ))
      }
    />
  );
}
