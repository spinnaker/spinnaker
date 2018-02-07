import * as React from 'react';
import { Link } from './link';

interface IParentPipelineLinkProps {
  parentPipelineExecutionId: string;
  application: string;
}

export const PipelineLink = ({ parentPipelineExecutionId, application }: IParentPipelineLinkProps) => {
  return (
    <Link
      targetState="home.applications.application.pipelines.executionDetails.execution"
      stateParams={{
        application,
        executionId: parentPipelineExecutionId,
      }}
      linkText="Pipeline"
    />
  );
};
