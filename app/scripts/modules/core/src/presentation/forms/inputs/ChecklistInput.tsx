import * as React from 'react';

import { createFakeReactSyntheticEvent, orEmptyString, validationClassName } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

interface IChecklistInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  options: string[];
}

export class ChecklistInput extends React.Component<IChecklistInputProps> {
  public render() {
    const { value, validation, inputClassName, options, onChange, ...otherProps } = this.props;
    const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const selected = e.target.value;
      let newValue = value.concat(selected);
      if (value.includes(selected)) {
        newValue = value.filter((v: string) => v !== selected);
      }
      onChange(createFakeReactSyntheticEvent({ value: newValue, name: e.target.name }));
    };

    return (
      <div className="checklist">
        {options.map(o => (
          <div className="checkbox" key={o}>
            <label>
              <input
                className={className}
                type="checkbox"
                value={o}
                onChange={e => handleChange(e)}
                checked={!!value.includes(o)}
                {...otherProps}
              />
              {o}
            </label>
          </div>
        ))}
      </div>
    );
  }
}
