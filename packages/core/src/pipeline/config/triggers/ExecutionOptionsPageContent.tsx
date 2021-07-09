import React from 'react';

import { IPipeline } from '../../../domain';
import { HelpField } from '../../../help';
import { CheckboxInput, FormField } from '../../../presentation';

export interface IExecutionOptionsPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function ExecutionOptionsPageContent(props: IExecutionOptionsPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;
  return (
    <div className="row">
      <div className="col-md-11 col-md-offset-1">
        <FormField
          input={(inputProps) => (
            <CheckboxInput
              {...inputProps}
              text={<strong>Disable concurrent pipeline executions (only run one at a time). </strong>}
            />
          )}
          onChange={() => {
            updatePipelineConfig({ limitConcurrent: !pipeline.limitConcurrent });
          }}
          value={pipeline.limitConcurrent}
        />
        {pipeline.limitConcurrent && (
          <FormField
            input={(inputProps) => (
              <CheckboxInput
                {...inputProps}
                text={
                  <>
                    <strong>Do not automatically cancel pipelines waiting in queue. </strong>
                    <HelpField id={'pipeline.config.parallel.cancel.queue'} />
                  </>
                }
              />
            )}
            onChange={() => {
              updatePipelineConfig({ keepWaitingPipelines: !pipeline.keepWaitingPipelines });
            }}
            value={pipeline.keepWaitingPipelines}
          />
        )}
      </div>
    </div>
  );
}
