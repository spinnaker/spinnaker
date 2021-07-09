import { extend } from 'lodash';
import React from 'react';

import { IParameter, IPipeline } from '../../../domain';

import { Parameters } from '../parameters/Parameters';

export interface IParametersPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function ParametersPageContent(props: IParametersPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;

  function addParameter() {
    const parameterConfig = pipeline.parameterConfig || [];
    const updatedParameterConfig = parameterConfig.concat({
      name: '',
      label: '',
      required: false,
      pinned: false,
      description: '',
      default: '',
      hasOptions: false,
      options: [{ value: '' }],
    });
    updatePipelineConfig({ parameterConfig: updatedParameterConfig });
  }

  function removeParameter(index: number): void {
    const parameterConfig = pipeline.parameterConfig.slice(0);
    parameterConfig.splice(index, 1);
    updatePipelineConfig({ parameterConfig });
  }

  function updateParameter(index: number, changes: Partial<IParameter>): void {
    const parameterConfig = pipeline.parameterConfig.slice(0);
    extend(parameterConfig[index], changes);
    updatePipelineConfig({ parameterConfig });
  }

  function updateAllParameters(parameters: IParameter[]): void {
    const parameterConfig = parameters.slice(0);
    updatePipelineConfig({ parameterConfig });
  }

  return (
    <Parameters
      addParameter={addParameter}
      parameters={pipeline.parameterConfig}
      pipelineName={pipeline.name}
      removeParameter={removeParameter}
      updateParameter={updateParameter}
      updateAllParameters={updateAllParameters}
    />
  );
}
