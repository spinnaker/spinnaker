import * as React from 'react';

import { FastPropertyPods } from './FastPropertyPods';
import { Property } from '../domain/property.domain';
import { Application } from 'core/application/application.model';
import { IGroupedProperties } from '../global/GlobalPropertiesList';

interface IProps {
  filteredProperties: Property[],
  groupedProperties?: IGroupedProperties,
  allProperties: Property[],
  application: Application,
  groupedBy: string
}

interface IState {
}

export const MAX_PROPERTIES_TO_DISPLAY = 500;

export class FastPropertiesList extends React.Component<IProps, IState> {
  public render() {
    const { filteredProperties, groupedProperties, groupedBy }  = this.props;
    const properties = groupedProperties || filteredProperties;
    return (
      <div>
        <div className="fast-property-wrapper">
          {this.props.filteredProperties.length > MAX_PROPERTIES_TO_DISPLAY && (
            <div className="text-center">
              <strong>Only showing the first {MAX_PROPERTIES_TO_DISPLAY} properties.</strong>
              <div>Use the filters above to narrow down the list.</div>
            </div>
          )}
          {this.props.filteredProperties.length > 0 && (
            <div>
              <FastPropertyPods properties={properties} groupedBy={groupedBy}/>
            </div>
          )}
        </div>
      </div>
    )
  }
}
