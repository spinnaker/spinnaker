import classNames from 'classnames';
import * as React from 'react';

const ARROW_CLASS = 'arrow-down';

export interface IHeaderArrowProps {
  arrowColor: string;
  className?: string;
}

export default ({ arrowColor, className }: IHeaderArrowProps) => (
  <div className={classNames(ARROW_CLASS, className)} style={{ borderTopColor: arrowColor }} />
);
