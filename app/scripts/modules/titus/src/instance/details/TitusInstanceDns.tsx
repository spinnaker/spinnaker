import * as React from 'react';
import {
  CollapsibleSection,
  CopyToClipboard,
  LabeledValue,
  LabeledValueList,
  LinkWithClipboard,
} from '@spinnaker/core';

export interface ITitusInstanceDnsProps {
  containerIp: string;
  host: string;
  instancePort: string;
  ipv6Address: string;
}

export const TitusInstanceDns = ({ containerIp, host, instancePort, ipv6Address }: ITitusInstanceDnsProps) => (
  <CollapsibleSection heading="DNS" defaultExpanded={true}>
    <LabeledValueList className="dl-horizontal dl-narrow">
      {containerIp && (
        <LabeledValue
          label="Container IP"
          value={
            <>
              {containerIp}
              <CopyToClipboard text={containerIp} toolTip="Copy container IP to clipboard" />
            </>
          }
        />
      )}
      {ipv6Address && (
        <LabeledValue
          label="Container IPv6"
          value={<LinkWithClipboard text={ipv6Address} url={`http://${ipv6Address}:${instancePort}`} />}
        />
      )}
      <LabeledValue label="Host IP" value={host} />
    </LabeledValueList>
  </CollapsibleSection>
);
