import React from 'react';
import { CloudProviderLabel, CloudProviderLogo } from 'core/cloudProvider';

export const FilterCheckbox = (props: {
  heading: string;
  sortFilterType: { [key: string]: boolean };
  onChange: () => void;
  isCloudProvider?: boolean;
}): JSX.Element => {
  const { heading, isCloudProvider, onChange, sortFilterType } = props;
  const changeHandler = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const value = target.type === 'checkbox' ? target.checked : target.value;
    sortFilterType[heading] = Boolean(value);
    onChange();
  };
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={Boolean(sortFilterType[heading])} onChange={changeHandler} />
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
