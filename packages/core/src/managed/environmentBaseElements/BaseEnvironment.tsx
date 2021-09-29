import classnames from 'classnames';
import React from 'react';

import './BaseEnvironment.less';

interface IEnvironmentSectionProps {
  name: string;
  size?: 'regular' | 'small';
  isPreview?: boolean;
  basedOn?: string | null;
  isDeleting?: boolean;
  gitMetadata?: {
    branch?: string;
    pullRequest?: {
      link?: string;
    };
  };
}

export const getEnvTitle = ({
  name,
  gitMetadata,
  isPreview,
}: Pick<IEnvironmentSectionProps, 'isPreview' | 'name' | 'gitMetadata'>) => {
  return isPreview ? gitMetadata?.branch || name : name;
};

const EnvironmentTitle = ({
  name,
  basedOn,
  gitMetadata,
  isPreview,
  isDeleting,
  size = 'regular',
}: IEnvironmentSectionProps) => {
  const baseTitle = (
    <div className={classnames('env-name', { 'preview-env': Boolean(isPreview) })}>
      {getEnvTitle({ name, isPreview, gitMetadata })}
    </div>
  );
  const link = gitMetadata?.pullRequest?.link;
  return (
    <div className={classnames('EnvironmentTitle', size)}>
      {link ? (
        <a href={link} target="_blank" className="env-link horizontal middle">
          {baseTitle} <i className="fas fa-external-link-alt sp-margin-s-left" />
        </a>
      ) : (
        baseTitle
      )}
      {basedOn && (
        <div>
          Based on <span className="uppercase">{basedOn}</span>
        </div>
      )}
      {isDeleting && <div className="env-deleting">Deleting</div>}
    </div>
  );
};

interface IBaseEnvironmentProps extends IEnvironmentSectionProps {
  className?: string;
}

export const BaseEnvironment: React.FC<IBaseEnvironmentProps> = ({ children, className, ...otherProps }) => {
  return (
    <section className={classnames('BaseEnvironment', className)}>
      <EnvironmentTitle {...otherProps} />
      {children}
    </section>
  );
};
