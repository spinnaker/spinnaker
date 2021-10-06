import { mount } from 'enzyme';
import React from 'react';

import { VpcTag } from './VpcTag';
import { VpcReader } from '../vpc/VpcReader';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('VpcTag', function () {
  describe('vpc tag rendering - no VPC provided', function () {
    it('displays default message when no vpcId supplied', function () {
      const component = mount(<VpcTag vpcId={undefined} />);
      expect(component.text()).toBe('None (EC2 Classic)');
    });

    it('displays default message when null vpcId supplied', function () {
      const component = mount(<VpcTag vpcId={null} />);
      expect(component.text()).toBe('None (EC2 Classic)');
    });
  });

  describe('vpc tag rendering - VPC provided', function () {
    it('displays vpc name when found', async function () {
      spyOn(VpcReader, 'getVpcName').and.returnValue(Promise.resolve('Main VPC'));
      const component = mount(<VpcTag vpcId="vpc-1" />);
      await tick();
      expect(component.text()).toBe('Main VPC (vpc-1)');
    });

    it('displays vpc id when not found', async function () {
      spyOn(VpcReader, 'getVpcName').and.returnValue(Promise.resolve(null));
      const component = mount(<VpcTag vpcId="vpc-2" />);
      await tick();
      expect(component.text()).toBe('(vpc-2)');
    });
  });
});
