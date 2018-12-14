import * as React from 'react';
import { isArray, isString, isObject } from 'lodash';
import * as classNames from 'classnames';

import { Tooltip } from 'core/presentation';

import { IWizardPageData } from './WizardModal';

interface IWizardStepLabelProps<T> {
  current: boolean;
  dirty: boolean;
  errors: { [key: string]: string };
  pageState: IWizardPageData<T>;
  onClick: (pageState: IWizardPageData<T>) => void;
  waiting: boolean;
}

export class WizardStepLabel<T> extends React.Component<IWizardStepLabelProps<T>> {
  private flattenErrors(errors: any) {
    const traverse = (obj: any, path: string, flattenedErrors: { [key: string]: any }): any => {
      if (isArray(obj)) {
        obj.forEach((elem, idx) => traverse(elem, `${path}[${idx}]`, flattenedErrors));
      } else if (isString(obj)) {
        flattenedErrors[path] = obj;
      } else if (isObject(obj)) {
        Object.keys(obj).forEach(key => traverse(obj[key], `${path}.${key}`, flattenedErrors));
      }

      return flattenedErrors;
    };

    return traverse(errors, 'errors', {});
  }

  public render() {
    const { current, dirty, errors, onClick, pageState, waiting } = this.props;

    const className = classNames({
      default: !pageState.props.done,
      dirty: dirty || !!errors,
      current,
      done: pageState.props.done,
      waiting,
    });

    const label = (
      <li className={className}>
        <a className="clickable" onClick={() => onClick(pageState)}>
          {pageState.label}
        </a>
      </li>
    );

    const flattenedErrors = this.flattenErrors(errors);
    const errorKeys = Object.keys(flattenedErrors);
    if (errorKeys.length) {
      const Errors = (
        <span>
          {errorKeys.map(key => (
            <span key={key}>
              {flattenedErrors[key]} <br />
            </span>
          ))}
        </span>
      );

      return <Tooltip template={Errors}>{label}</Tooltip>;
    }
    return label;
  }
}
