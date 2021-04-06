import * as React from 'react';
import { timestamp } from '@spinnaker/core';

export interface IDateLabelProps {
  dateIso: string;
}

export default function FormattedDate({ dateIso }: IDateLabelProps) {
  if (dateIso) {
    return <span>{timestamp(new Date(dateIso).getTime())}</span>;
  } else {
    return <span>N/A</span>;
  }
}
