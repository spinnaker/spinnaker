import { isEqual } from 'lodash';
import memoizeOne from 'memoize-one';
import React from 'react';
import { Option } from 'react-select';

interface IStringOptionsProps {
  strings?: string[];
  render?: (options: Array<Option<string>>) => React.ReactNode;
  children?: (options: Array<Option<string>>) => React.ReactNode;
}

interface IStringOptionsState {
  options: Array<Option<string>>;
}

const makeOptions = (options: string[]): Array<Option<string>> => {
  options = options || [];
  return options.map((str) => ({ label: str, value: str }));
};

/**
 * Converts an array of strings to an array of Options[] suitable for use with react-select
 *
 * Example:
 * <StringsAsOptions strings={apps}>
 *   {options => <SelectInput onChange={...} clearable={false} options={options}/>}
 * </StringsAsOptions>
 */
export class StringsAsOptions extends React.Component<IStringOptionsProps, IStringOptionsState> {
  private memoizedMakeOptions = memoizeOne(makeOptions, isEqual);

  public render() {
    const options = this.memoizedMakeOptions(this.props.strings);
    const render = this.props.render || this.props.children;
    return render(options);
  }
}
