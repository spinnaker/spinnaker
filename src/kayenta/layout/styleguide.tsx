import * as React from 'react';
import * as classNames from 'classnames';

import 'kayenta/canary.less';

export const STYLEGUIDE_CLASS = 'styleguide';
export const KAYENTA_CLASS = 'kayenta';

export interface IStyleguideProps {
  children: JSX.Element | JSX.Element[];
  className?: string;
}

export default function Styleguide({ children, className }: IStyleguideProps) {
  return (
    <div className={classNames(STYLEGUIDE_CLASS, KAYENTA_CLASS, className)}>
      {children}
    </div>
  );
}
