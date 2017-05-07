import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { FastPropertyPods } from './FastPropertyPods';
import { Property } from '../domain/property.domain';
import { Application } from 'core/application/application.model';
import { collapsibleSectionStateCache } from 'core/cache/collapsibleSectionStateCache';
import { Sticky } from 'core/utils/stickyHeader/Sticky';
import { IGroupedProperties } from '../global/GlobalPropertiesList';

interface IProps {
  filteredProperties: Property[],
  groupedProperties?: IGroupedProperties,
  allProperties: Property[],
  application: Application,
  groupedBy: string
}

interface IState {
  open: boolean,
}

export const MAX_PROPERTIES_TO_DISPLAY = 500;

@autoBindMethods
export class FastPropertiesList extends React.Component<IProps, IState> {

  private sectionCacheKey = ['#global', 'fastProperty', 'properties'].join('#');

  constructor(props: IProps) {
    super(props);
    this.sectionCacheKey = [this.props.application.name, 'fastProperty', 'properties'].join('#');
    this.state = {
      open: this.props.application.global || collapsibleSectionStateCache.isSet(this.sectionCacheKey) || collapsibleSectionStateCache.isExpanded(this.sectionCacheKey)
    };
  }

  public toggleProperties(): void {
    const open = !this.state.open;
    this.setState({open});
    collapsibleSectionStateCache.setExpanded(this.sectionCacheKey, open);
  }

  public render() {
    const chevronClass = `small glyphicon toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`;
    const { filteredProperties, groupedProperties, groupedBy, application }  = this.props;
    const properties = groupedProperties || filteredProperties;
    const isFiltered = filteredProperties.length < this.props.allProperties.length;
    return (
      <div>
        {!application.global && (<Sticky className="clickable rollup-title sticky-header" onClick={this.toggleProperties}>
          <span className={chevronClass}/>
          <h4 className="shadowed">
            Application Properties (
            {isFiltered && (<span>{this.props.filteredProperties.length} of {this.props.allProperties.length}</span>)}
            {!isFiltered && (<span>{this.props.allProperties.length}</span>)}
            )
          </h4>
        </Sticky>)}
        {this.state.open && (
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
        )}
      </div>
    )
  }
}
