import * as React from 'react';
import * as classNames from 'classnames';

import './footer.less';

export interface IFooterProps {
  children?: JSX.Element;
}

export default function Footer({ children }: IFooterProps) {
  return (
    <div className={classNames('row', 'canary-footer')}>
      {children}
    </div>
  );
}
