import React from 'react';

import { Application } from 'core/application';
import { CategoryDropdown } from './CategoryDropdown';
import { IDataSourceCategory } from './ApplicationHeader';

export interface IApplicationNavProps {
  application: Application;
  categories: IDataSourceCategory[];
  activeCategory: IDataSourceCategory;
  primary: boolean;
}

export const ApplicationNavSection = ({ application, categories, activeCategory }: IApplicationNavProps) => {
  if (application.notFound || application.hasError) {
    return null;
  }
  return (
    <div className="hidden-xs application-nav">
      <div className="nav-section horizontal middle">
        {categories.map(category => (
          <CategoryDropdown
            key={category.key}
            category={category}
            activeCategory={activeCategory}
            application={application}
          />
        ))}
      </div>
    </div>
  );
};
