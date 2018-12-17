import * as React from 'react';
import { Option } from 'react-select';

import {
  IFormInputProps,
  isStringArray,
  Markdown,
  orEmptyString,
  OmitControlledInputPropsFrom,
  StringsAsOptions,
  validationClassName,
} from '@spinnaker/core';

import 'cloudfoundry/common/cloudFoundry.less';

interface ICloudFoundryRadioButtonInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.TextareaHTMLAttributes<any>> {
  options: Array<string | Option<string | number>>;
  inputClassName?: string;
}

export const CloudFoundryRadioButtonInput = (props: ICloudFoundryRadioButtonInputProps) => {
  const { value, validation, inputClassName, options, ...otherProps } = props;
  const className = `RadioButtonInput radio ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  const RadioButtonsElement = ({ opts }: { opts: Array<Option<string>> }) => (
    <div className="horizontal">
      {opts.map(option => (
        <div key={option.label} className={className}>
          <label className="cloud-foundry-radio-button">
            <input type="radio" {...otherProps} value={option.value} checked={option.value === value} />
            <span className="marked">
              <Markdown message={option.label} />
            </span>
          </label>
        </div>
      ))}
    </div>
  );

  if (isStringArray(options)) {
    return <StringsAsOptions strings={options}>{opts => <RadioButtonsElement opts={opts} />}</StringsAsOptions>;
  } else {
    return <RadioButtonsElement opts={options as Array<Option<string>>} />;
  }
};
