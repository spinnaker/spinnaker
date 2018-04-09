import * as React from 'react';
import { xor } from 'lodash';
import { BindAll } from 'lodash-decorators';

export interface IChecklistProps {
  includeSelectAllButton?: boolean;
  inline?: boolean;
  items: Set<string>;
  checked: Set<string>;
  onChange: (checked: Set<string>) => void;
}

export interface IChecklistState {}

@BindAll()
export class Checklist extends React.Component<IChecklistProps, IChecklistState> {
  constructor(props: IChecklistProps) {
    super(props);
  }

  private handleChecked(event: React.ChangeEvent<HTMLInputElement>): void {
    const name = event.target.name;
    const isChecked = event.target.checked;
    const checked = new Set(this.props.checked);

    isChecked ? checked.add(name) : checked.delete(name);
    this.props.onChange(checked);
  }

  public render(): React.ReactElement<Checklist> {
    const { checked, includeSelectAllButton, inline, items, onChange } = this.props;

    const showSelectAll = includeSelectAllButton && items.size > 1;
    const allSelected = xor([...items], [...checked]).length === 0;
    const selectAllLabel = allSelected ? 'Deselect All' : 'Select All';
    const handleSelectAll = () => (allSelected ? onChange(new Set()) : onChange(new Set(items)));

    if (!inline) {
      return (
        <ul className="checklist">
          {[...items].map(item => (
            <li key={item}>
              <label>
                <input name={item} checked={checked.has(item)} type="checkbox" onChange={this.handleChecked} />
                {' ' + item}
              </label>
            </li>
          ))}
          {showSelectAll && (
            <li>
              <a className="btn btn-default btn-xs push-left clickable" onClick={handleSelectAll}>
                {selectAllLabel}
              </a>
            </li>
          )}
        </ul>
      );
    }

    return (
      <span>
        {[...items].map(item => (
          <label key={item} className="checkbox-inline">
            <input name={item} checked={checked.has(item)} type="checkbox" onChange={this.handleChecked} />
            {item}
          </label>
        ))}
        {showSelectAll && (
          <a className="btn btn-default btn-xs clickable" style={{ margin: '8px 0 0 10px' }} onClick={handleSelectAll}>
            {selectAllLabel}
          </a>
        )}
      </span>
    );
  }
}
