import { IExecutionDetailsProps } from 'core/domain';
import React from 'react';

export interface IExecutionDetailsSectionWrapperProps {
  children?: any;
  name: string;
  current: string;
}

export type IExecutionDetailsSectionProps = IExecutionDetailsSectionWrapperProps & IExecutionDetailsProps;

export const ExecutionDetailsSection = (props: IExecutionDetailsSectionWrapperProps) => {
  if (props.current === props.name) {
    return <div className="step-section-details">{props.children}</div>;
  }
  return null;
};
