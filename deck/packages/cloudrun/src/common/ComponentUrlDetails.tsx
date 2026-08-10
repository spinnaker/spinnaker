import React from 'react';

import { CopyToClipboard } from '@spinnaker/core';

export function ComponentUrlDetails({ component }: { component: { url?: string; httpsUrl?: string } }) {
  if (!component?.url) {
    return null;
  }

  return (
    <>
      <dt>HTTPS</dt>
      <dd className="small">
        <a href={component.url} target="_blank" rel="noopener noreferrer">
          {component.url}
        </a>
        <CopyToClipboard
          className="copy-to-clipboard copy-to-clipboard-sm"
          toolTip="Copy URL to clipboard"
          text={component.httpsUrl || component.url}
        />
      </dd>
    </>
  );
}
