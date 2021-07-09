import React from 'react';

import { IPipelineRef } from '../../../../domain';

export interface IPipelineRefListProps {
  title: string;
  pipelineRefs: IPipelineRef[];
}

export const PipelineRefList = (props: IPipelineRefListProps): any => {
  const pipelineRefs = props.pipelineRefs || [];
  if (pipelineRefs.length === 0) {
    return <div />;
  } else {
    return (
      <div className="row">
        <div className="col-md-9">
          <div className="row">
            <div className="col-md-9">
              <strong>{props.title}</strong>
            </div>
          </div>
          {pipelineRefs.map((pipelineRef, i) => (
            <div key={i} className="row">
              <div className="col-md-9">
                <a
                  href={`/#/applications/${pipelineRef.application}/executions/${
                    pipelineRef.id ? `configure/${pipelineRef.id}` : ''
                  }`}
                >
                  {pipelineRef.application} - {pipelineRef.name}
                </a>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }
};
