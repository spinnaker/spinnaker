export const HOURS = [...new Array(24).keys()].map((i) => {
  return {
    label: i < 10 ? `0${i}` : `${i}`,
    value: `${i}`,
  };
});

export const MINUTES = [...new Array(60).keys()].map((i) => {
  return {
    label: i < 10 ? `0${i}` : `${i}`,
    value: `${i}`,
  };
});

export const WEEKDAYS = [
  { label: 'Sunday', value: 'SUN' },
  { label: 'Monday', value: 'MON' },
  { label: 'Tuesday', value: 'TUE' },
  { label: 'Wednesday', value: 'WED' },
  { label: 'Thursday', value: 'THU' },
  { label: 'Friday', value: 'FRI' },
  { label: 'Saturday', value: 'SAT' },
];

export const MONTHS_DAYS_WITH_LASTS = ['1W', ...[...new Array(31).keys()].map((x) => `${x + 1}`), 'LW', 'L'].map(
  (i) => {
    if (i === 'L') {
      return { label: 'Last Day', value: i };
    } else if (i === 'LW') {
      return { label: 'Last Weekday', value: i };
    } else if (i === '1W') {
      return { label: 'First Weekday', value: i };
    } else {
      if (i.length > 1) {
        const secondToLastDigit = i.charAt(i.length - 2);
        if (secondToLastDigit === '1') {
          return { label: `${i}th Day`, value: i };
        }
      }
      const lastDigit = i.charAt(i.length - 1);
      switch (lastDigit) {
        case '1':
          return { label: `${i}st Day`, value: i };
        case '2':
          return { label: `${i}nd Day`, value: i };
        case '3':
          return { label: `${i}rd Day`, value: i };
        default:
          return { label: `${i}th Day`, value: i };
      }
    }
  },
);

export const MONTH_WEEKS = [
  { label: 'First', value: '#1' },
  { label: 'Second', value: '#2' },
  { label: 'Third', value: '#3' },
  { label: 'Fourth', value: '#4' },
  { label: 'Fifth', value: '#5' },
  { label: 'Last', value: 'L' },
];
