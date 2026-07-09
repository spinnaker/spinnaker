import React from 'react';

import { CollapsibleSection, JsonUtils } from '@spinnaker/core';

export function DcosJsonLink({ value }: { value: any }) {
  const [expanded, setExpanded] = React.useState(false);

  return (
    <>
      <a className="clickable" onClick={() => setExpanded(!expanded)}>
        {expanded ? 'Hide JSON' : 'Show JSON'}
      </a>
      {expanded && <pre>{JsonUtils.makeSortedStringFromObject(value)}</pre>}
    </>
  );
}

export function DcosMapSection({ heading, value }: { heading: string; value: Record<string, any> }) {
  if (!value || Object.keys(value).length === 0) {
    return null;
  }

  return (
    <CollapsibleSection heading={heading} defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        {Object.keys(value).map((key) => (
          <React.Fragment key={key}>
            <dt>{key}</dt>
            <dd>{String(value[key])}</dd>
          </React.Fragment>
        ))}
      </dl>
    </CollapsibleSection>
  );
}

export function DcosLink({ href }: { href?: string }) {
  if (!href) {
    return <dd>-</dd>;
  }

  return (
    <dd>
      <a href={href} target="_blank" rel="noopener noreferrer">
        Open in DC/OS
      </a>
    </dd>
  );
}
