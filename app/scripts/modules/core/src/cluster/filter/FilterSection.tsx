import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { HelpField } from 'core/help/HelpField';

export interface IFilterSectionProps {
  heading: string;
  expanded?: boolean;
  helpKey?: string;
}

export interface IFilterSectionState {
  expanded: boolean;
}

@BindAll()
export class FilterSection extends React.Component<IFilterSectionProps, IFilterSectionState> {
  constructor(props: IFilterSectionProps) {
    super(props);
    this.state = { expanded: props.expanded };
  }

  public getIcon() {
    return this.state.expanded ? 'down' : 'right';
  }

  public toggle() {
    this.setState({ expanded: !this.state.expanded });
  }

  public render() {
    return (
      <div className="collapsible-filter-section">
        <div className="section-heading clickable" onClick={this.toggle}>
          <h4>
            <span className={`glyphicon glyphicon-chevron-${this.getIcon()}`}/>
            {` ${this.props.heading}`}
            {this.props.helpKey && (<span> <HelpField id={this.props.helpKey} placement="right"/></span>)}
          </h4>
        </div>
        { this.state.expanded && (
          <div className="content-body">
            {this.props.children}
          </div>
        )}
      </div>
    );
  }
}
