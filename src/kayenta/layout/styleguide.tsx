import * as React from 'react';
import * as classNames from 'classnames';

import 'kayenta/canary.less';

export const STYLEGUIDE_CLASS = 'styleguide';
export const KAYENTA_CLASS = 'kayenta';

export interface IStyleguideProps {
  children: JSX.Element | JSX.Element[];
}

export default function Styleguide({children}: IStyleguideProps) {
  return (
    <div className={classNames(STYLEGUIDE_CLASS, KAYENTA_CLASS)}>
      {children}
    </div>
  );
}
