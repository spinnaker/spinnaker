import React from 'react';

import { HelpField } from 'core/help/HelpField';
import './FilterSearch.less';

export interface IFilterSearchProps {
  helpKey?: string;
  onBlur: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onSearchChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  value: string;
}

export const FilterSearch = ({ helpKey, onBlur, onSearchChange, value }: IFilterSearchProps) => {
  return (
    <div className="filter-search">
      <form className="horizontal middle space-between" role="form">
        <div className="form-group nav-search">
          <input
            type="search"
            className="form-control input-med sp-form"
            value={value}
            onBlur={onBlur}
            onChange={onSearchChange}
            placeholder="Search"
          />
        </div>
        {helpKey && <HelpField id={helpKey} placement="right" />}
      </form>
    </div>
  );
};
