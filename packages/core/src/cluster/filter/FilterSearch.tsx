import React from 'react';

import { HelpField } from '../../help/HelpField';
import { TextInput } from '../../presentation';

import './FilterSearch.less';

export interface IFilterSearchProps {
  helpKey?: string;
  onBlur: (event: React.FocusEvent<HTMLInputElement>) => void;
  onSearchChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  value: string;
}

export const FilterSearch = ({ helpKey, onBlur, onSearchChange, value }: IFilterSearchProps) => (
  <form className="FilterSearch flex-container-h baseline" role="form" onSubmit={(e) => e.preventDefault()}>
    <TextInput type="search" value={value} onBlur={onBlur} onChange={onSearchChange} placeholder="Search by field" />
    {helpKey && <HelpField id={helpKey} placement="right" />}
  </form>
);
