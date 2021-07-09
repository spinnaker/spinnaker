import { isString } from 'lodash';
import React from 'react';

import { Markdown } from '../../Markdown';
import { ICategorizedErrors, IValidationCategory } from './categories';

import './ValidationMessage.less';

const containerClassNames: ICategorizedErrors = {
  async: 'infoMessage',
  error: 'errorMessage',
  info: 'infoMessage',
  message: 'infoMessage',
  success: 'successMessage',
  warning: 'warningMessage',
};

const iconClassNames: ICategorizedErrors = {
  async: 'fa fa-spinner fa-spin',
  error: 'fa fa-exclamation-circle',
  info: 'fa fa-info-circle',
  message: 'icon-view-1',
  success: 'far fa-check-circle',
  warning: 'fa icon-alert-triangle',
};

export interface IValidationMessageProps {
  message: React.ReactNode;
  type: IValidationCategory | undefined;
  /**
   * The (optional) class name to apply to the icon.
   * If none is provided, the icon is determined by the value of category.
   * If false to provided, no icon will be rendered
   */
  iconClassName?: string | false;
  /**
   * The (optional) class name to apply to the ValidationMessage.
   * If none is provided, the class is determined by the value of category.
   */
  containerClassName?: string;
}

export const ValidationMessage = (props: IValidationMessageProps) => {
  const { type, message, iconClassName, containerClassName } = props;
  const showIcon = iconClassName !== false;
  return (
    <div className={`ValidationMessage ${containerClassName || containerClassNames[type] || ''}`}>
      {showIcon && <i className={iconClassName || iconClassNames[type] || ''} />}
      <div className="message">{isString(message) ? <Markdown message={message} /> : message}</div>
    </div>
  );
};
