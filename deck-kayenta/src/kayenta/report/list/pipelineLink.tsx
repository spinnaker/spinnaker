import { useSref } from '@uirouter/react';
import * as React from 'react';

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
