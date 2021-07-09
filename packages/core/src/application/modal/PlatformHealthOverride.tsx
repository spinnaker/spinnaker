import { isEqual } from 'lodash';
import React from 'react';

import { HelpField } from '../../help/HelpField';
import { createFakeReactSyntheticEvent, IFormInputProps } from '../../presentation';

export interface IPlatformHealthOverrideProps {
  interestingHealthProviderNames: string[];
  onChange: (healthProviderNames: string[]) => void;
  platformHealthType: string;
  showHelpDetails?: boolean;
}

export class PlatformHealthOverride extends React.Component<IPlatformHealthOverrideProps> {
  private clicked = (event: React.ChangeEvent<HTMLInputElement>) => {
    const interestingHealthProviderNames = event.target.checked ? [this.props.platformHealthType] : null;
    this.props.onChange(interestingHealthProviderNames);
  };

  public render() {
    return (
      <div className="checkbox">
        <label>
          <input
            type="checkbox"
            checked={isEqual(this.props.interestingHealthProviderNames, [this.props.platformHealthType])}
            onChange={this.clicked}
          />
          Consider only {this.props.platformHealthType} health
        </label>{' '}
        <HelpField id="application.platformHealthOnly" expand={this.props.showHelpDetails} />
      </div>
    );
  }
}

export function PlatformHealthOverrideInput(
  props: IFormInputProps & { platformHealthType: string; showHelpDetails?: boolean },
) {
  const { value, onChange, platformHealthType, showHelpDetails, ...rest } = props;
  return (
    <PlatformHealthOverride
      {...rest}
      interestingHealthProviderNames={value}
      platformHealthType={platformHealthType}
      showHelpDetails={showHelpDetails}
      onChange={(newVal: string[]) => {
        onChange(createFakeReactSyntheticEvent({ name: rest.name, value: newVal }));
      }}
    />
  );
}
