import React from 'react';

export interface ICostFactorProps {
  min: number;
  max?: number;
}

export const CostFactor = ({ min, max }: ICostFactorProps) => {
  const MAX_DOLLARS = 4;
  const minDollars = (
    <>
      <span className="cost">{'$'.repeat(min)}</span>
      {'$'.repeat(MAX_DOLLARS - min)}
    </>
  );
  const maxDollars = (
    <>
      <span className="cost">{'$'.repeat(Math.min(MAX_DOLLARS, max))}</span>
      {'$'.repeat(MAX_DOLLARS - Math.min(MAX_DOLLARS, max))}
    </>
  );

  return (
    <span className={'cost-factor'}>
      {minDollars}
      {max ? ` - ${maxDollars}` : ''}
    </span>
  );
};
