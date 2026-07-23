import { InfrastructureCaches } from '@spinnaker/core';

import { SecurityGroupSelector } from './SecurityGroupSelector';

describe('SecurityGroupSelector', () => {
  it('renders when the security group cache has not been registered', () => {
    spyOn(InfrastructureCaches, 'get').and.returnValue(undefined);

    expect(() => new SecurityGroupSelector(buildProps() as any)).not.toThrow();
  });
});

function buildProps() {
  return {
    command: { selectedProvider: 'aws' },
    availableGroups: [],
    groupsToEdit: [],
    onChange: jasmine.createSpy('onChange'),
  };
}
