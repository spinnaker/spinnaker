import * as React from 'react';

import { createFakeReactSyntheticEvent, isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

interface IChecklistInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  options?: IChecklistInputOption[];
  stringOptions?: string[];
}

export interface IChecklistInputOption {
  label: string;
  value: string;
}

export class ChecklistInput extends React.Component<IChecklistInputProps> {
  public render() {
    const { value, validation, inputClassName, options, stringOptions, onChange, ...otherProps } = this.props;
    const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const selected = e.target.value;
      let newValue = value.concat(selected);
      if (value.includes(selected)) {
        newValue = value.filter((v: string) => v !== selected);
      }
      onChange(createFakeReactSyntheticEvent({ value: newValue, name: e.target.name }));
    };

    let checkListOptions = options || [];
    if (isStringArray(stringOptions)) {
      checkListOptions = stringOptions.map(s => ({ label: s, value: s }));
    }

    return (
      <div className="checklist">
        {checkListOptions.map(o => (
          <div className="checkbox" key={o.value}>
            <label>
              <input
                className={className}
                type="checkbox"
                value={o.value}
                onChange={e => handleChange(e)}
                checked={!!value.includes(o.value)}
                {...otherProps}
              />
              {o.label}
            </label>
          </div>
        ))}
      </div>
    );
  }
}
