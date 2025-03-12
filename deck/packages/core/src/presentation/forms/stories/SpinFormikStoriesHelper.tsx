import React from 'react';

import { createFakeReactSyntheticEvent } from '../';

export const URLComponent = ({
  value,
  onBlur,
  onChange,
}: {
  value: string;
  onBlur: (e: React.ChangeEvent) => void;
  onChange: (newURL: string) => void;
}) => {
  const domain = value.split('https://')?.[1] || '';
  const handleChange = (e: React.ChangeEvent<any>) => {
    onChange(`https://${e.target.value}`);
  };
  return (
    <div style={{ display: 'flex' }}>
      <div
        style={{
          alignItems: 'center',
          backgroundColor: '#eee',
          border: '1px solid rgb(226, 232, 240)',
          borderRightColor: 'transparent',
          borderRadius: '0.25rem 0px 0px 0.25rem',
          display: 'flex',
          marginRight: -1,
          padding: '0.5rem 1rem',
        }}
      >
        https://
      </div>
      <input
        style={{
          border: '1px solid rgb(226, 232, 240)',
          borderRadius: '0px 0.25rem 0.25rem 0px',
          display: 'flex',
          outline: 'none',
          padding: '0.5rem 1rem',
        }}
        value={domain}
        onBlur={onBlur}
        onChange={handleChange}
      />
    </div>
  );
};

export const URLComponentInput = ({
  name,
  value,
  onBlur,
  onChange,
}: {
  name?: string;
  value?: string;
  onBlur?: (e: React.ChangeEvent) => void;
  onChange?: (e: React.ChangeEvent) => void;
}) => {
  const handleChange = (url: string) => {
    onChange(createFakeReactSyntheticEvent({ name, value: url }));
  };
  const handleBlur = () => {
    onBlur(createFakeReactSyntheticEvent({ name, value }));
  };
  return <URLComponent value={value} onBlur={handleBlur} onChange={handleChange} />;
};
