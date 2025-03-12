import React from 'react';
import type { ICollapsibleSectionProps } from '../../../presentation';
import { CollapsibleSection } from '../../../presentation';

interface IArtifactCollapsibleSectionProps extends ICollapsibleSectionProps {
  isUpdating?: boolean;
}

export const ArtifactCollapsibleSection: React.FC<IArtifactCollapsibleSectionProps> = ({
  heading,
  isUpdating,
  ...props
}) => {
  return (
    <CollapsibleSection
      outerDivClassName="artifact-section"
      bodyClassName="sp-padding-xl-bottom sp-padding-l-left"
      toggleClassName="artifact-section-title sp-margin-l-bottom"
      enableCaching={false}
      expandIconSize="14px"
      expandIconPosition="left"
      heading={({ chevron }) => (
        <div className="horizontal middle">
          {chevron}
          {heading}
          {isUpdating && <span className="in-progress-dot" />}
        </div>
      )}
      {...props}
    />
  );
};
