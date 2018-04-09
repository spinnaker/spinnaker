import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ReactInjector } from 'core/reactShims';
import { IDataSourceCategory } from './ApplicationHeader';

export interface ITinyHeaderProps {
  primaryCategories?: IDataSourceCategory[];
  secondaryCategories?: IDataSourceCategory[];
  activeCategory?: IDataSourceCategory;
}

@BindAll()
export class TinyHeader extends React.Component<ITinyHeaderProps> {
  private renderCategory(category: IDataSourceCategory): JSX.Element {
    if (category.dataSources.length === 1) {
      return (
        <option value={category.dataSources[0].key} key={category.key}>
          {category.dataSources[0].label}
        </option>
      );
    }
    return (
      <optgroup label={category.label} key={category.key}>
        {category.dataSources.map(ds => (
          <option value={ds.key} key={ds.key}>
            {ds.label}
          </option>
        ))}
      </optgroup>
    );
  }

  private categoryChanged(event: React.ChangeEvent<HTMLSelectElement>) {
    const { primaryCategories, secondaryCategories } = this.props;
    const { $state } = ReactInjector;
    const key = event.target.value;
    const categoryMatch = primaryCategories
      .concat(secondaryCategories)
      .find(c => c.dataSources.some(ds => ds.key === key));
    if (!categoryMatch) {
      return;
    }
    const dsMatch = categoryMatch.dataSources.find(ds => ds.key === key);
    $state.go(dsMatch.sref, null, { relative: this.getApplicationState() });
  }

  private getApplicationState(): string {
    return ReactInjector.$state.current.name.split('.application.')[0] + '.application';
  }

  private getCurrentValue(): string {
    const { $state } = ReactInjector;
    const appState = this.getApplicationState();
    const { primaryCategories, secondaryCategories } = this.props;
    const categoryMatch = primaryCategories
      .concat(secondaryCategories)
      .find(c => c.dataSources.some(ds => $state.includes(appState + ds.sref)));
    if (!categoryMatch) {
      return null;
    }
    return categoryMatch.dataSources.find(ds => $state.includes(appState + ds.sref)).key;
  }

  public render() {
    const { primaryCategories, secondaryCategories } = this.props;
    const currentValue = this.getCurrentValue();
    return (
      <div className="visible-xs" style={{ marginRight: '10px' }}>
        <select className="form-control input-sm" onChange={this.categoryChanged} value={currentValue}>
          {primaryCategories.concat(secondaryCategories).map(this.renderCategory)}
        </select>
      </div>
    );
  }
}
