import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { HelpField } from 'core/help/HelpField';
import { ReactInjector } from 'core/reactShims';

export interface ICollapsibleSectionProps {
  bodyClassName?: string;
  cacheKey?: string;
  defaultExpanded?: boolean;
  heading: (() => JSX.Element) | string;
  helpKey?: string;
  subsection?: boolean;
}

export interface ICollapsibleSectionState {
  cacheKey: string;
  expanded: boolean;
  headingIsString: boolean;
}

@BindAll()
export class CollapsibleSection extends React.Component<ICollapsibleSectionProps, ICollapsibleSectionState> {
  constructor(props: ICollapsibleSectionProps) {
    super(props);

    const headingIsString = typeof props.heading === 'string' || props.heading instanceof String;
    const cacheKey = props.cacheKey || (headingIsString ? (props.heading as string) : undefined);
    this.state = {
      cacheKey,
      headingIsString,
      expanded: ReactInjector.collapsibleSectionStateCache.isSet(cacheKey)
        ? ReactInjector.collapsibleSectionStateCache.isExpanded(cacheKey)
        : props.defaultExpanded,
    };
  }

  public toggle(): void {
    const { cacheKey, expanded } = this.state;
    this.setState({ expanded: !expanded });
    ReactInjector.collapsibleSectionStateCache.setExpanded(cacheKey, !expanded);
  }

  public render() {
    const { bodyClassName, children, heading, helpKey, subsection } = this.props;
    const { expanded, headingIsString } = this.state;

    const Heading = headingIsString ? heading : (heading as () => JSX.Element)();

    const prefix = subsection ? 'sub' : '';
    const icon = expanded ? 'down' : 'right';

    return (
      <div className={`collapsible-${prefix}section`}>
        <a className={`clickable section-${prefix}heading`} onClick={this.toggle}>
          <h4 className={`collapsible-${prefix}heading`}>
            <span className={`glyphicon glyphicon-chevron-${icon}`} /> {Heading}
            {helpKey && <HelpField id={helpKey} placement="right" />}
          </h4>
        </a>
        {expanded && <div className={`content-body ${bodyClassName ? bodyClassName : ''}`}>{children}</div>}
      </div>
    );
  }
}
