import React from 'react';

import type {
  GceLoadBalancerType,
  IGceLoadBalancerDataItem,
  IGceLoadBalancerListener,
  IGceResourceReference,
} from '../common';

export interface IGceHttpLoadBalancerListenerEditorProps {
  addresses: IGceLoadBalancerDataItem[];
  certificates: IGceLoadBalancerDataItem[];
  listener: IGceLoadBalancerListener;
  loadBalancerType: GceLoadBalancerType;
  onChange: (listener: IGceLoadBalancerListener) => void;
  onRemove: () => void;
  subnets: IGceLoadBalancerDataItem[];
}

function selectedReference(name: string, options: IGceLoadBalancerDataItem[]): IGceResourceReference | undefined {
  if (!name) {
    return undefined;
  }
  return (options.find((option) => option.name === name) || { name }) as IGceResourceReference;
}

export function GceHttpLoadBalancerListenerEditor({
  addresses,
  certificates,
  listener,
  loadBalancerType,
  onChange,
  onRemove,
  subnets,
}: IGceHttpLoadBalancerListenerEditorProps): JSX.Element {
  const protocols = ['HTTP', 'HTTPS'];

  return (
    <fieldset className="gce-http-listener">
      <legend>Listener</legend>
      <label>
        Name
        <input
          data-testid="listener-name"
          required
          type="text"
          value={listener.name}
          onChange={(event) => onChange({ ...listener, name: event.target.value })}
        />
      </label>
      <label>
        Protocol
        <select
          data-testid="listener-protocol"
          value={listener.protocol}
          onChange={(event) => {
            const protocol = event.target.value as IGceLoadBalancerListener['protocol'];
            onChange({ ...listener, ...(protocol === 'HTTPS' ? { portRange: '443' } : {}), protocol });
          }}
        >
          {protocols.map((protocol) => (
            <option key={protocol} value={protocol}>
              {protocol}
            </option>
          ))}
        </select>
      </label>
      <label>
        Port
        <input
          data-testid="listener-port"
          disabled={listener.protocol === 'HTTPS'}
          required
          type="text"
          value={listener.portRange}
          onChange={(event) => onChange({ ...listener, portRange: event.target.value })}
        />
      </label>
      {loadBalancerType === 'INTERNAL_MANAGED' && (
        <label>
          Subnet
          <select
            data-testid="listener-subnet"
            required
            value={listener.subnet?.name || ''}
            onChange={(event) => onChange({ ...listener, subnet: selectedReference(event.target.value, subnets) })}
          >
            <option value="">Select...</option>
            {subnets.map((subnet) => (
              <option key={subnet.name} value={subnet.name}>
                {subnet.name}
              </option>
            ))}
          </select>
        </label>
      )}
      <label>
        {loadBalancerType === 'INTERNAL_MANAGED' ? 'Internal IP' : 'External IP'}
        <select
          data-testid="listener-address"
          value={listener.address?.name || ''}
          onChange={(event) => onChange({ ...listener, address: selectedReference(event.target.value, addresses) })}
        >
          <option value="">Ephemeral</option>
          {addresses.map((address) => (
            <option key={address.name} value={address.name}>
              {address.name}
            </option>
          ))}
        </select>
      </label>
      {listener.protocol === 'HTTPS' && (
        <>
          <label>
            Certificate
            <select
              data-testid="listener-certificate"
              value={listener.certificate?.name || ''}
              onChange={(event) =>
                onChange({
                  ...listener,
                  certificate: selectedReference(event.target.value, certificates),
                  certificateMap: undefined,
                })
              }
            >
              <option value="">Select...</option>
              {certificates.map((certificate) => (
                <option key={certificate.name} value={certificate.name}>
                  {certificate.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Certificate map
            <input
              data-testid="listener-certificate-map"
              type="text"
              value={listener.certificateMap || ''}
              onChange={(event) =>
                onChange({ ...listener, certificate: undefined, certificateMap: event.target.value || undefined })
              }
            />
          </label>
        </>
      )}
      <button type="button" className="btn btn-sm btn-default" onClick={onRemove}>
        Remove listener
      </button>
    </fieldset>
  );
}
