import classnames from 'classnames';
import React from 'react';

import './BaseEnvironment.less';

interface IEnvironmentTitleProps {
  size?: 'regular' | 'small';
}

export const EnvironmentTitle: React.FC<IEnvironmentTitleProps> = ({ size = 'regular', children }) => {
  return <div className={classnames('EnvironmentTitle', size)}>{children}</div>;
};

interface IEnvironmentSectionProps {
  title?: string;
  size?: 'regular' | 'small';
}

export const BaseEnvironment: React.FC<IEnvironmentSectionProps> = ({ title, size = 'regular', children }) => {
  return (
    <section className="BaseEnvironment">
      {title && <EnvironmentTitle size={size}>{title}</EnvironmentTitle>}
      {children}
    </section>
  );
};
