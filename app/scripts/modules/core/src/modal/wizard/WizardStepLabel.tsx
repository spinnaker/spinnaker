import { isArray, isObject, isString } from 'lodash';
import React from 'react';

import { WizardPage } from './WizardPage';
import { Tooltip } from '../../presentation';

interface IWizardStepLabelProps {
  current: boolean;
  onClick: (page: WizardPage<any>) => void;
  page: WizardPage<any>;
}

const flattenErrors = (errors: any) => {
  const traverse = (obj: any, path: string, flattenedErrors: { [key: string]: any }): any => {
    if (isArray(obj)) {
      obj.forEach((elem, idx) => traverse(elem, `${path}[${idx}]`, flattenedErrors));
    } else if (isString(obj)) {
      flattenedErrors[path] = obj;
    } else if (isObject(obj)) {
      Object.keys(obj).forEach((key) => traverse(obj[key], `${path}.${key}`, flattenedErrors));
    }

    return flattenedErrors;
  };

  return traverse(errors, 'errors', {});
};

export class WizardStepLabel extends React.Component<IWizardStepLabelProps> {
  public render() {
    const { current, page, onClick } = this.props;
    const { errors, status } = page.state;
    const { label } = page.props;

    const flattenedErrors: { [field: string]: string } = flattenErrors(errors);
    const hasErrors = !!Object.keys(flattenedErrors).length;
    const className = `${WizardPage.getStatusClass(status)} ${current ? 'current' : ''}`;

    const pageLabel = (
      <li className={className}>
        <a className="clickable" onClick={() => onClick(page)}>
          {label}
        </a>
      </li>
    );

    if (!hasErrors) {
      return pageLabel;
    }

    const Errors = (
      <span>
        {Object.keys(flattenedErrors).map((key) => (
          <span key={key}>
            {flattenedErrors[key]} <br />
          </span>
        ))}
      </span>
    );

    return <Tooltip template={Errors}>{pageLabel}</Tooltip>;
  }
}
