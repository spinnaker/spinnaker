import { module } from 'angular';
import { get } from 'lodash';
import React from 'react';
import { react2angular } from 'react2angular';

import { FormField, IStage, ReactSelectInput } from '../../../../../index';
import { withErrorBoundary } from '../../../../../presentation/SpinErrorBoundary';
import { STATUS_OPTIONS } from './stageStatusOptions';

interface IStageStatusPreconditionConfigProps {
  preconditionContext: any;
  upstreamStages: IStage[];
  updatePreconditionContext: (context: any) => void;
}

export function StageStatusPreconditionConfig({
  preconditionContext,
  upstreamStages,
  updatePreconditionContext,
}: IStageStatusPreconditionConfigProps) {
  return (
    <div className="form-group row">
      <FormField
        input={(inputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            options={upstreamStages.map((stage) => ({ value: stage.name, label: stage.name }))}
          />
        )}
        label="Stage"
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          updatePreconditionContext({
            ...preconditionContext,
            stageName: e.target.value,
          })
        }
        validate={(stageName) => {
          if (!stageName) {
            return 'Please select a stage';
          }
          return null;
        }}
        value={get(preconditionContext, 'stageName', null)}
      />
      <FormField
        input={(inputProps) => <ReactSelectInput {...inputProps} clearable={false} options={STATUS_OPTIONS} />}
        label="Status"
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          updatePreconditionContext({
            ...preconditionContext,
            stageStatus: e.target.value,
          })
        }
        validate={(stageStatus) => {
          if (!stageStatus) {
            return 'Please select a status';
          }
          return null;
        }}
        value={get(preconditionContext, 'stageStatus', null)}
      />
    </div>
  );
}

export const STAGE_STATUS_PRECONDITION_CONFIG = 'spinnaker.core.stageStatusPreconditionSelector';
module(STAGE_STATUS_PRECONDITION_CONFIG, []).component(
  'stageStatusPreconditionConfig',
  react2angular(withErrorBoundary(StageStatusPreconditionConfig, 'stageStatusPreconditionConfig'), [
    'preconditionContext',
    'upstreamStages',
    'updatePreconditionContext',
  ]),
);
