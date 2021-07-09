import React from 'react';

import { IExecutionDetailsProps } from '../../../../domain';

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
