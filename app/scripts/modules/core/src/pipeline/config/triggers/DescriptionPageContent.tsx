import React from 'react';

import { IPipeline } from 'core/domain';
import { FormField, TextAreaInput } from 'core/presentation';

export interface IDescriptionPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export function DescriptionPageContent(props: IDescriptionPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;

  return (
    <div className="row">
      <div className="col-md-12">
        <FormField
          name={'description'}
          input={(inputProps) => (
            <TextAreaInput
              {...inputProps}
              placeholder={
                '(Optional) anything that might be helpful to explain the purpose of this pipeline; Markdown is okay'
              }
              rows={3}
            />
          )}
          value={pipeline.description}
          onChange={(event) => {
            updatePipelineConfig({ description: event.target.value });
          }}
        />
      </div>
    </div>
  );
}
