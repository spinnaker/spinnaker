import React, { useState } from 'react';

import type { IPipeline } from '../../../domain';
import { HelpField } from '../../../help';
import { CheckboxInput, FormField, NumberInput } from '../../../presentation';

export interface IExecutionOptionsPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function ExecutionOptionsPageContent(props: IExecutionOptionsPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;
  const [currentMaxConcurrent, setCurrentMaxConcurrent] = useState(pipeline.maxConcurrentExecutions || 0);
  const handleMaxConcurrentChange = (changeEvent: any) => {
    const value = Number.parseInt(changeEvent.target.value);
    setCurrentMaxConcurrent(value);
    updatePipelineConfig({ maxConcurrentExecutions: value });
  };

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
            if (!pipeline.limitConcurrent && !pipeline.keepWaitingPipelines) {
              pipeline.keepWaitingPipelines = true;
              updatePipelineConfig({ keepWaitingPipelines: true });
            }
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
        {!pipeline.limitConcurrent && (
          <div>
            <label className="col-md-3 sm-label-right">
              Maximum concurrent pipeline executions <HelpField id={'pipeline.config.parallel.max.concurrent'} />
            </label>
            <div className="col-md-8">
              <FormField
                input={(inputProps) => <NumberInput {...inputProps} min={0} max={65534} />}
                onChange={handleMaxConcurrentChange}
                value={currentMaxConcurrent}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
