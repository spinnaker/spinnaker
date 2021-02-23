import { dump as dumpYaml } from 'js-yaml';
import React from 'react';

interface IRenderOutputFileProps {
  outputFileObject: object;
}

/**
 * Renders an object as YAML.
 * Transforms strings that look like URLs into links
 * The intention is to render the output file from a Script or Run Job stage
 */
export const RenderOutputFile = React.memo(({ outputFileObject }: IRenderOutputFileProps) => {
  const linkRegex = /(https?:\/\/[^$\s'"]+)/;
  const isLink = (str: string) => `'${str}'`.match(linkRegex);

  const yaml = dumpYaml(outputFileObject);

  let linkCount = 0;
  const segments = yaml.split(linkRegex);
  const renderLink = (url: string) => (
    <a key={`${linkCount++}`} href={url} target="_blank">
      {url}
    </a>
  );
  const renderSegment = (segment: string) => (isLink(segment) ? renderLink(segment) : segment);
  return <pre style={{ overflow: 'scroll', maxHeight: '400px' }}>{segments.map(renderSegment)}</pre>;
});
