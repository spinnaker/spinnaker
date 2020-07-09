import * as React from 'react';
import { useSref } from '@uirouter/react';

interface IParentPipelineLinkProps {
  parentPipelineExecutionId: string;
  application: string;
}

export const PipelineLink = ({ parentPipelineExecutionId, application }: IParentPipelineLinkProps) => {
  const sref = useSref('home.applications.application.pipelines.executionDetails.execution', {
    application,
    executionId: parentPipelineExecutionId,
  });
  return <a {...sref}>Pipeline</a>;
};
