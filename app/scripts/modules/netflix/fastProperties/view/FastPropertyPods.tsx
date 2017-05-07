import * as React from 'react';

import { Property } from '../domain/property.domain';
import { IGroupedProperties } from '../global/GlobalPropertiesList';
import { FastPropertyTable } from './FastPropertyTable';

interface IProps {
  properties: Property[] | IGroupedProperties,
  groupedBy: string;
}

interface IState {

}

export class FastPropertyPods extends React.Component<IProps, IState> {

  constructor(props: IProps) {
    super(props);
  }

  public render() {
    const { groupedBy, properties } = this.props;
    if (groupedBy === 'none') {
      return (<FastPropertyTable groupedBy={groupedBy} properties={properties as Property[]}/>)
    }
    const propertyGroups = properties as IGroupedProperties;
    // limit to 500
    let total = 0;
    const limitedGroups = {} as IGroupedProperties;
    Object.keys(propertyGroups).forEach(key => {
      if (total < 500) {
        limitedGroups[key] = propertyGroups[key];
      }
      total += propertyGroups[key].length;
    });
    const pods = Object.keys(limitedGroups).map(key => {
      return (
        <div key={key}>
          <h4><span className="glyphicon glyphicon-equalizer"/> {key}</h4>
          <FastPropertyTable groupedBy={groupedBy} properties={limitedGroups[key]}/>
        </div>
      );
    });
    return (
      <div>{pods}</div>
    );
  }
}
