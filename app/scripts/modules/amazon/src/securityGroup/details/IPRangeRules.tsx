import React from 'react';
import { Overridable, CollapsibleSection, LabeledValue, LabeledValueList } from '@spinnaker/core';

export interface IIPRangeRulesProps {
  ipRules: Array<IPRangeRulesDetails>;
}
export interface IPRangeRulesDetails {
  rules: Array<IRangeRule>;
  address: String;
}

export interface IRangeRule {
  startPort: number;
  endPort: number;
  protocol: string;
}

@Overridable('aws.securityGroup.ip.rules')
export class IPRangeRules extends React.Component<IIPRangeRulesProps> {
  public render() {
    const ipRules = this.props.ipRules || [];
    console.log(ipRules.length);
    const heading = 'IP Ranage Rules(' + ipRules.length + ')';
    return (
      <CollapsibleSection heading={heading}>
        {ipRules.map(rule => (
          <LabeledValueList className="horizontal-when-filters-collapsed">
            <LabeledValue label="IP Range" value={rule.address} />
            <LabeledValue
              label="Port Ranges"
              value={rule.rules.map(r =>
                r.protocol === '-1' ? (
                  <span>
                    All ports and protocols
                    {rule.rules.length > 1 ? (
                      <div>
                        <em>Additional port ranges are specified, but redundant:</em>
                      </div>
                    ) : null}
                  </span>
                ) : (
                  <div>
                    <span>{r.protocol + ':' + r.startPort + '->' + r.endPort}</span>
                  </div>
                ),
              )}
            />
          </LabeledValueList>
        ))}
      </CollapsibleSection>
    );
  }
}
