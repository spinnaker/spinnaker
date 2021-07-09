import React from 'react';

import { CloudProviderLabel, CloudProviderLogo } from '../cloudProvider';

/** Some filter models need direct control over the change event
 * to propagate the state of the checkbox to the parent component.
 * When this is the case use onChangeEvent, otherwise use onChange
 * @param onChangeEvent
 * @param onChange
 * One of the two change functions must be provided
 */

export const FilterCheckbox = (props: {
  heading: string;
  sortFilterType: { [key: string]: boolean };
  onChange?: () => void;
  onChangeEvent?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  isCloudProvider?: boolean;
  name?: string;
}): JSX.Element => {
  const { heading, isCloudProvider, name, onChange, onChangeEvent, sortFilterType } = props;
  const changeHandler = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    sortFilterType[heading] = Boolean(value);
    onChange();
  };
  return (
    <div className="checkbox">
      <label>
        <input
          type="checkbox"
          checked={Boolean(sortFilterType && sortFilterType[heading])}
          onChange={onChangeEvent || changeHandler}
          name={name}
        />
        {!isCloudProvider ? (
          heading
        ) : (
          <>
            <CloudProviderLogo provider="heading" height="'14px'" width="'14px'" />
            <CloudProviderLabel provider={heading} />
          </>
        )}
      </label>
    </div>
  );
};
