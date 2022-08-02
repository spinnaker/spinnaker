import { module } from 'angular';
import { chain } from 'lodash';
import React from 'react';
import type { AutocompleteResult, Option } from 'react-select';
import { Async } from 'react-select';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';

interface ITimezoneSelectProps {
  availableTimezones: string[];
  selectedTimezone: string;
  selectTimezone: (timezone: string, target?: any) => void;
  target?: any;
}

export class TimezoneSelect extends React.Component<ITimezoneSelectProps> {
  private loadOptions = (inputValue: string): Promise<AutocompleteResult<string>> => {
    return new Promise((resolve) => {
      if (!inputValue || inputValue.length < 3) {
        resolve({
          options: [],
          complete: false,
        });
      } else {
        const filteredOptions = chain(this.props.availableTimezones)
          .filter((i) => i.toLowerCase().includes(inputValue))
          .map((i) => ({ value: i, label: i }))
          .value();
        resolve({
          options: filteredOptions,
          complete: false,
        });
      }
    });
  };

  public render() {
    return (
      <Async
        cache={null}
        clearable={false}
        ignoreAccents={false}
        loadOptions={this.loadOptions}
        onChange={(selected: Option<string>) => this.props.selectTimezone(selected.value, this.props.target)}
        placeholder="Type at least 3 characters to search for an timezone..."
        searchPromptText="Type at least 3 characters to search for an timezone..."
        value={{ value: this.props.selectedTimezone, label: this.props.selectedTimezone }}
        required={true}
      />
    );
  }
}

export const GCE_TIMEZONE_SELECT = 'spinnaker.gce.timezoneSelect';
module(GCE_TIMEZONE_SELECT, []).component(
  'gceTimezoneSelect',
  react2angular(withErrorBoundary(TimezoneSelect, 'gceTimezoneSelect'), [
    'availableTimezones',
    'selectedTimezone',
    'selectTimezone',
    'target',
  ]),
);
