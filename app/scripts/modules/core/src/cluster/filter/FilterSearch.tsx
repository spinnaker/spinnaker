import React from 'react';

import { HelpField } from '../../help/HelpField';

import './FilterSearch.less';

export interface IFilterSearchProps {
  helpKey?: string;
  onBlur: (event: React.FocusEvent<HTMLInputElement>) => void;
  onSearchChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  value: string;
}

export const FilterSearch = ({ helpKey, onBlur, onSearchChange, value }: IFilterSearchProps) => (
  <div className="FilterSearch sp-margin-s-right">
    <form className="horizontal middle" role="form" onSubmit={(e) => e.preventDefault()}>
      <div className="form-group nav-search">
        <input
          type="search"
          className="form-control input-med sp-form"
          value={value}
          onBlur={onBlur}
          onChange={onSearchChange}
          placeholder="Search by field"
        />
      </div>
      {helpKey && <HelpField id={helpKey} placement="right" />}
    </form>
  </div>
);
