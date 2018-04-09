import * as React from 'react';
import { IExecutionDetailsComponentProps } from 'core/domain';

export interface IExecutionDetailsSectionProps
  extends IExecutionDetailsComponentProps,
    IExecutionDetailsSectionWrapperProps {}

export interface IExecutionDetailsSectionWrapperProps {
  children?: any;
  name: string;
  current: string;
}

export const ExecutionDetailsSection: React.StatelessComponent<IExecutionDetailsSectionWrapperProps> = (
  props: IExecutionDetailsSectionWrapperProps,
): JSX.Element => {
  if (props.current === props.name) {
    return <div className="step-section-details">{props.children}</div>;
  }
  return null;
};
