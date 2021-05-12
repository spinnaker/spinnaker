import cx from 'classnames';
import React from 'react';

import { CollapsibleSectionStateCache } from 'core/cache';
import { Icon } from 'core/index';

export interface ICollapsibleSectionProps {
  outerDivClassName?: string;
  toggleClassName?: string;
  headingClassName?: string;
  bodyClassName?: string;
  useGlyphiconChevron?: boolean;
  chevronColor?: string;
  cacheKey?: string;
  enableCaching?: boolean;
  defaultExpanded?: boolean;
  heading: ((props: { chevron: JSX.Element }) => JSX.Element) | string;
}

export interface ICollapsibleSectionState {
  cacheKey: string;
  expanded: boolean;
}

export class CollapsibleSection extends React.Component<ICollapsibleSectionProps, ICollapsibleSectionState> {
  public static defaultProps: Partial<ICollapsibleSectionProps> = {
    outerDivClassName: 'collapsible-section',
    toggleClassName: 'clickable section-heading',
    headingClassName: 'collapsible-heading',
    bodyClassName: 'content-body',
    cacheKey: undefined as string,
    useGlyphiconChevron: true,
    enableCaching: true,
  };

  constructor(props: ICollapsibleSectionProps) {
    super(props);

    const cacheKey = props.cacheKey || (typeof props.heading === 'string' ? (props.heading as string) : undefined);
    const expanded =
      props.enableCaching && CollapsibleSectionStateCache.isSet(cacheKey)
        ? CollapsibleSectionStateCache.isExpanded(cacheKey)
        : props.defaultExpanded;

    this.state = { cacheKey, expanded };
  }

  private toggle = (): void => {
    const { cacheKey, expanded } = this.state;
    this.setState({ expanded: !expanded });
    if (this.props.enableCaching) {
      CollapsibleSectionStateCache.setExpanded(cacheKey, !expanded);
    }
  };

  public render() {
    const {
      outerDivClassName,
      toggleClassName,
      headingClassName,
      bodyClassName,
      children,
      heading,
      useGlyphiconChevron,
      chevronColor,
    } = this.props;
    const { expanded } = this.state;

    const chevron = useGlyphiconChevron ? (
      <span
        className="glyphicon glyphicon-chevron-right section-heading-chevron"
        style={{ transform: `rotate(${expanded ? 90 : 0}deg)`, color: chevronColor }}
      />
    ) : (
      <Icon
        name="accordionCollapse"
        size="16px"
        className={cx(['section-heading-chevron', { 'rotated-90': !expanded }])}
        color={chevronColor || 'concrete'}
      />
    );
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
