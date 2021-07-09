import React from 'react';

import { StageConfigField } from '../common';
import { IStage } from '../../../../domain';
import { HelpField } from '../../../../help';
import { RadioButtonInput } from '../../../../presentation';

import './overrideFailure.less';

export interface IOverrideFailureConfigProps {
  failPipeline: boolean;
  continuePipeline: boolean;
  completeOtherBranchesThenFail: boolean;
  updateStageField: (changes: Partial<IStage>) => void;
}

export const OverrideFailure = (props: IOverrideFailureConfigProps) => {
  const overrideFailureOptions = [
    {
      label: 'halt the entire pipeline',
      value: 'fail',
      help: <HelpField id="pipeline.config.haltPipelineOnFailure" />,
    },
    {
      label: 'halt this branch of the pipeline',
      value: 'stop',
      help: <HelpField id="pipeline.config.haltBranchOnFailure" />,
    },
    {
      label: 'halt this branch and fail the pipeline once other branches complete',
      value: 'faileventual',
      help: <HelpField id="pipeline.config.haltBranchOnFailureFailPipeline" />,
    },
    {
      label: 'ignore the failure',
      value: 'ignore',
      help: <HelpField id="pipeline.config.ignoreFailure" />,
    },
  ];

  const getFailureOption = () => {
    let initValue = 'fail';
    if (props.completeOtherBranchesThenFail === true) {
      initValue = 'faileventual';
    } else if (props.failPipeline === true && props.continuePipeline === false) {
      initValue = 'fail';
    } else if (props.failPipeline === false && props.continuePipeline === false) {
      initValue = 'stop';
    } else if (props.failPipeline === false && props.continuePipeline === true) {
      initValue = 'ignore';
    }
    return initValue;
  };

  const failureOptionChanged = (value: string) => {
    if (value === 'fail') {
      props.updateStageField({
        failPipeline: true,
        continuePipeline: false,
        completeOtherBranchesThenFail: false,
      });
    } else if (value === 'stop') {
      props.updateStageField({
        failPipeline: false,
        continuePipeline: false,
        completeOtherBranchesThenFail: false,
      });
    } else if (value === 'ignore') {
      props.updateStageField({
        failPipeline: false,
        continuePipeline: true,
        completeOtherBranchesThenFail: false,
      });
    } else if (value === 'faileventual') {
      props.updateStageField({
        failPipeline: false,
        continuePipeline: false,
        completeOtherBranchesThenFail: true,
      });
    }
  };

  return (
    <StageConfigField label="If stage fails">
      <RadioButtonInput
        inputClassName={'override-failure-radio-input'}
        options={overrideFailureOptions}
        value={getFailureOption()}
        onChange={(e: any) => failureOptionChanged(e.target.value)}
      />
    </StageConfigField>
  );
};
