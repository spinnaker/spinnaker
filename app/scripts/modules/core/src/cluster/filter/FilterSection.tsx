import React from 'react';

import { HelpField } from 'core/help/HelpField';

export interface IFilterSectionProps {
  heading: string;
  expanded?: boolean;
  helpKey?: string;
}

export interface IFilterSectionState {
  expanded: boolean;
}

export class FilterSection extends React.Component<IFilterSectionProps, IFilterSectionState> {
  constructor(props: IFilterSectionProps) {
    super(props);
    this.state = { expanded: props.expanded };
  }

  public getIcon() {
    return this.state.expanded ? 'down' : 'right';
  }

  public toggle = () => {
    this.setState({ expanded: !this.state.expanded });
  };

  public render() {
    const chevronStyle = {
      transform: this.state.expanded ? 'rotate(90deg)' : 'rotate(0deg)',
      transition: 'all ease 0.15s',
    };

    return (
      <div className="collapsible-filter-section">
        <div className="section-heading clickable" onClick={this.toggle}>
          <h4>
            <span className={`glyphicon glyphicon-chevron-right`} style={chevronStyle} />
            {` ${this.props.heading}`}
            {this.props.helpKey && (
              <span>
                {' '}
                <HelpField id={this.props.helpKey} placement="right" />
              </span>
            )}
          </h4>
        </div>
        {this.state.expanded && <div className="content-body">{this.props.children}</div>}
      </div>
    );
  }
}
