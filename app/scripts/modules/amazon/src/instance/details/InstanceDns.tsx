import * as React from 'react';
import { LabeledValue, LabeledValueList, LinkWithClipboard } from '@spinnaker/core';

interface IIpv6Address {
  ip: string;
  url: string;
}

export interface IInstanceDnsProps {
  instancePort: string;
  ipv6Addresses: IIpv6Address[];
  permanentIps: string[];
  privateDnsName: string;
  privateIpAddress: string;
  publicDnsName: string;
  publicIpAddress: string;
}

export const InstanceDns = ({
  instancePort,
  ipv6Addresses,
  permanentIps,
  privateDnsName,
  privateIpAddress,
  publicDnsName,
  publicIpAddress,
}: IInstanceDnsProps) => (
  <LabeledValueList className="horizontal-when-filters-collapsed">
    {privateDnsName && (
      <LabeledValue
        label="Private DNS Name"
        value={<LinkWithClipboard text={privateDnsName} url={`http://${privateDnsName}:${instancePort}`} />}
      />
    )}
    {publicDnsName && (
      <LabeledValue
        label="Public DNS Name"
        value={<LinkWithClipboard text={publicDnsName} url={`http://${publicDnsName}:${instancePort}`} />}
      />
    )}
    {privateIpAddress && (
      <LabeledValue
        label="Private IP Address"
        value={<LinkWithClipboard text={privateIpAddress} url={`http://${privateIpAddress}:${instancePort}`} />}
      />
    )}
    {permanentIps?.length && (
      <LabeledValue
        label="Permanent IP Address"
        value={permanentIps.map((ip) => (
          <LinkWithClipboard key={ip} text={ip} url={`http://${ip}:${instancePort}`} />
        ))}
      />
    )}
    {publicIpAddress && (
      <LabeledValue
        label="Public IP Address"
        value={<LinkWithClipboard text={publicIpAddress} url={`http://${publicIpAddress}:${instancePort}`} />}
      />
    )}
    {ipv6Addresses?.length && (
      <LabeledValue
        label={`IPv6 Address${ipv6Addresses.length > 1 ? 'es' : ''}`}
        value={ipv6Addresses.map((ipv6) => (
          <LinkWithClipboard key={ipv6.ip} text={ipv6.ip} url={ipv6.url} />
        ))}
      />
    )}
  </LabeledValueList>
);
