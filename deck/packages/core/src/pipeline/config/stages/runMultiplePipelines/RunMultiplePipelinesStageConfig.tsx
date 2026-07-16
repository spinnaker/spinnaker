import React from 'react';

import { FormikStageConfig } from '../FormikStageConfig';
import { InputYamlForm } from './InputYamlForm';
import type { IStageConfigProps } from '../common';

export function RunMultiplePipelinesStageConfig(props: IStageConfigProps) {
  return (
    <div className="RunMultiplePipelinesStageConfig">
      <FormikStageConfig {...props} onChange={props.updateStage} render={(props) => <InputYamlForm {...props} />} />
    </div>
  );
}
