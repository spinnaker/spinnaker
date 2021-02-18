import { CollapsibleSectionStateCache } from 'core/cache';
import React from 'react';

export interface ICollapsibleSectionProps {
  outerDivClassName?: string;
  toggleClassName?: string;
  headingClassName?: string;
  bodyClassName?: string;

  cacheKey?: string;
  defaultExpanded?: boolean;
  heading: ((props: { chevron: JSX.Element }) => JSX.Element) | string;
}

export interface ICollapsibleSectionState {
  cacheKey: string;
  expanded: boolean;
}

export class CollapsibleSection extends React.Component<ICollapsibleSectionProps, ICollapsibleSectionState> {
  public static defaultProps = {
    outerDivClassName: 'collapsible-section',
    toggleClassName: 'clickable section-heading',
    headingClassName: 'collapsible-heading',
    bodyClassName: 'content-body',
    cacheKey: undefined as string,
  };

  constructor(props: ICollapsibleSectionProps) {
    super(props);

    const cacheKey = props.cacheKey || (typeof props.heading === 'string' ? (props.heading as string) : undefined);
    const expanded = CollapsibleSectionStateCache.isSet(cacheKey)
      ? CollapsibleSectionStateCache.isExpanded(cacheKey)
      : props.defaultExpanded;

    this.state = { cacheKey, expanded };
  }

  private toggle = (): void => {
    const { cacheKey, expanded } = this.state;
    this.setState({ expanded: !expanded });
    CollapsibleSectionStateCache.setExpanded(cacheKey, !expanded);
  };

  public render() {
    const { outerDivClassName, toggleClassName, headingClassName, bodyClassName, children, heading } = this.props;
    const { expanded } = this.state;

    const chevronStyle = {
      transform: `rotate(${expanded ? 90 : 0}deg)`,
      transition: 'transform 0.15s ease',
    };

    const chevron = <span className="glyphicon glyphicon-chevron-right" style={chevronStyle} />;
    const Heading =
      typeof heading === 'string' ? (
        <h4 className={headingClassName}>
          {chevron} {heading}
        </h4>
      ) : (
        <>{heading({ chevron })}</>
      );

    return (
      <div className={outerDivClassName}>
        <a className={toggleClassName} onClick={this.toggle}>
          {Heading}
        </a>

        {expanded && <div className={bodyClassName}>{children}</div>}
      </div>
    );
  }
}
