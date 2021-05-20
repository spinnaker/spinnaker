import classnames from 'classnames';
import React from 'react';

import { IIconProps } from '@spinnaker/presentation';
import { CollapsibleSectionStateCache } from 'core/cache';
import { Icon } from 'core/index';

export interface ICollapsibleSectionProps {
  outerDivClassName?: string;
  toggleClassName?: string;
  headingClassName?: string;
  bodyClassName?: string;
  cacheKey?: string;
  enableCaching?: boolean;
  defaultExpanded?: boolean;
  expandIconPosition?: 'left' | 'right';
  expandIconType?: 'arrow' | 'plus';
  heading: ((props: { chevron: JSX.Element }) => JSX.Element) | string;
}

export interface ICollapsibleSectionState {
  cacheKey: string;
  expanded: boolean;
}

const rotationByPosition = {
  left: {
    expanded: 'rotate-0',
    collapsed: 'rotate-m90',
  },
  right: {
    expanded: 'rotate-p180',
    collapsed: 'rotate-p270',
  },
};

export const CollapsibleSection: React.FC<ICollapsibleSectionProps> = ({
  outerDivClassName = 'collapsible-section',
  toggleClassName = 'section-heading',
  headingClassName = 'collapsible-heading',
  bodyClassName = 'content-body',
  enableCaching = true,
  cacheKey: cacheKeyInternal,
  defaultExpanded,
  heading,
  expandIconPosition = 'left',
  expandIconType = 'arrow',
  children,
}) => {
  const cacheKey = React.useMemo(
    () => cacheKeyInternal || (typeof heading === 'string' ? (heading as string) : undefined),
    [cacheKeyInternal, heading],
  );
  const [isExpanded, setIsExpanded] = React.useState(
    enableCaching && CollapsibleSectionStateCache.isSet(cacheKey)
      ? CollapsibleSectionStateCache.isExpanded(cacheKey)
      : defaultExpanded,
  );

  const toggle = () => {
    setIsExpanded(!isExpanded);
    if (enableCaching) {
      CollapsibleSectionStateCache.setExpanded(cacheKey, !isExpanded);
    }
  };

  const expandIconProps: Partial<IIconProps> = {
    size: '16px',
    color: 'concrete',
  };

  const expandIcon = (
    <span className={classnames('section-heading-chevron', expandIconPosition)}>
      {expandIconType === 'arrow' ? (
        <Icon
          name="accordionCollapse"
          className={rotationByPosition[expandIconPosition][isExpanded ? 'expanded' : 'collapsed']}
          {...expandIconProps}
        />
      ) : (
        <Icon name={isExpanded ? 'minus' : 'plus'} {...expandIconProps} />
      )}
    </span>
  );

  const Heading =
    typeof heading === 'string' ? (
      <h4 className={headingClassName}>
        {expandIconPosition === 'left' ? (
          <>
            {expandIcon} {heading}
          </>
        ) : (
          <>
            {heading} {expandIcon}
          </>
        )}
      </h4>
    ) : (
      <>{heading({ chevron: expandIcon })}</>
    );

  return (
    <div className={outerDivClassName}>
      <a className={classnames(toggleClassName, 'clickable')} onClick={toggle}>
        {Heading}
      </a>

      {isExpanded && <div className={bodyClassName}>{children}</div>}
    </div>
  );
};
