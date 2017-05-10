import * as React from 'react';
import { Subscription } from 'rxjs/Subscription';
import autoBindMethods from 'class-autobind-decorator';

import { fastPropertyTime } from 'core/utils/timeFormatters';
import { Property } from '../domain/property.domain';
import { stateEvents } from 'core/state.events';
import { $stateParams, $state } from 'core/uirouter';
import { Tooltip } from 'core/presentation/Tooltip';
import { MAX_PROPERTIES_TO_DISPLAY } from './FastPropertiesList';

interface IProps {
  properties: Property[];
  groupedBy: string;
}

interface IState {
  activeRow: string;
}

@autoBindMethods
export class FastPropertyTable extends React.Component<IProps, IState> {

  private locationChangeSuccessSubscription: Subscription;

  public constructor(props: IProps) {
    super(props);
    this.state = {
      activeRow: $stateParams.propertyId,
    };
    this.locationChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => this.stateChanged());
  }

  private stateChanged(): void {
    this.setState({activeRow: $state.params.propertyId});
  }

  public componentWillUnmount(): void {
    this.locationChangeSuccessSubscription.unsubscribe();
  }

  private rowClicked(event: React.MouseEvent<HTMLElement>): void {
    let target = event.target as HTMLElement;
    while (target.nodeName !== 'TR') {
      target = target.parentElement;
    }
    const propertyId = target.getAttribute('data-property-id');

    this.setState({activeRow: propertyId});

    $state.go('.', {propertyId});
  }

  private toggleSort(event: React.MouseEvent<HTMLElement>): void {
    let sortBy = (event.target as HTMLElement).getAttribute('data-sort-key');
    if ($stateParams.sortBy === sortBy) {
      sortBy = '-' + sortBy;
    }
    if (sortBy === 'key') { // default value, just remove from view
      sortBy = null;
    }
    $state.go('.', {sortBy});
  }

  private makeHeaderRow(width: number, sortKey: string, label: string): JSX.Element {
    const isSortedBy = $stateParams.sortBy === sortKey;
    const isReverseSortedBy = $stateParams.sortBy === '-' + sortKey;
    return (
      <th key={sortKey} className="clickable" width={width + '%'} data-sort-key={sortKey} onClick={this.toggleSort}>
        {label}
        {isSortedBy && <span className="fa fa-caret-down"/>}
        {isReverseSortedBy && <span className="fa fa-caret-up"/>}
      </th>
    )
  }

  public render() {
    const overflowAtChars = 75;
    const headerCells = [
      this.makeHeaderRow(19, 'key', 'Property'),
      this.makeHeaderRow(19, 'value', 'Value'),
      this.makeHeaderRow(12, 'appId', 'Application'),
      this.makeHeaderRow(7, 'env', 'Env'),
      this.makeHeaderRow(10, 'region', 'Region'),
      this.makeHeaderRow(8, 'stack', 'Stack'),
      this.makeHeaderRow(10, 'baseOfScope', 'Scope'),
      this.makeHeaderRow(15, 'ts', 'Updated'),
    ];
    const head = (
      <thead>
        <tr>
          {headerCells}
        </tr>
      </thead>);

    const rows = this.props.properties.slice(0, MAX_PROPERTIES_TO_DISPLAY).map(property => {
      const accountLabelClass = `label label-default account-tag account-tag-${property.env}`;
      const rowClass = `small clickable ${this.state.activeRow === property.propertyId ? 'info' : ''}`;
      const longProp = (property.value || '').length > overflowAtChars;
      return (
        <tr key={property.propertyId + property.env} data-property-id={property.propertyId} className={rowClass}>
          <td>{property.key}</td>
          <td>
            {!longProp && <span>{property.value || ' '}</span>}
            {longProp && (
              <div>
                <span>{(property.value || '').substr(0, overflowAtChars)}</span>
                <Tooltip value={property.value} placement="right">
                  <span>&hellip;</span>
                </Tooltip>
              </div>
            )}
          </td>
          <td>{property.appId}</td>
          <td><span className={accountLabelClass}>{property.env}</span></td>
          <td>{property.region}</td>
          <td>{property.stack}</td>
          <td>{property.baseOfScope}</td>
          <td>{fastPropertyTime(property.ts)}</td>
        </tr>
      )
    });

    return (
      <table className="table table-hover fast-property-table" style={{wordBreak: 'break-all'}}>
        {head}
        <tbody onClick={this.rowClicked}>
          {rows}
        </tbody>
      </table>
    );

  }
}
