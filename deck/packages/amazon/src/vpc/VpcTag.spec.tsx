import { render, screen } from '@testing-library/react';
import React from 'react';

import { VpcTag } from './VpcTag';
import { VpcReader } from '../vpc/VpcReader';

describe('VpcTag', function () {
  describe('vpc tag rendering - no VPC provided', function () {
    it('displays default message when no vpcId supplied', function () {
      render(<VpcTag vpcId={undefined} />);
      expect(screen.getByText('None (EC2 Classic)')).toBeInTheDocument();
    });

    it('displays default message when null vpcId supplied', function () {
      render(<VpcTag vpcId={null} />);
      expect(screen.getByText('None (EC2 Classic)')).toBeInTheDocument();
    });
  });

  describe('vpc tag rendering - VPC provided', function () {
    it('displays vpc name when found', async function () {
      vi.spyOn(VpcReader, 'getVpcName').mockReturnValue(Promise.resolve('Main VPC'));
      render(<VpcTag vpcId="vpc-1" />);
      expect(await screen.findByText('Main VPC (vpc-1)')).toBeInTheDocument();
    });

    it('displays vpc id when not found', async function () {
      vi.spyOn(VpcReader, 'getVpcName').mockReturnValue(Promise.resolve(null));
      render(<VpcTag vpcId="vpc-2" />);
      expect(await screen.findByText('(vpc-2)')).toBeInTheDocument();
    });
  });
});
