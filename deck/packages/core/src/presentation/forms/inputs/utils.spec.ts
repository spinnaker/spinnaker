import { validationClassName } from './utils';

import '../../main.less';

const dangerBorderColor = 'rgb(255, 0, 0)';

function renderedBorderColor(validation: any, inputClassName = ''): string {
  const input = document.createElement('input');
  input.className = `form-control ${inputClassName} ${validationClassName(validation)}`;
  document.body.appendChild(input);

  const borderColor = window.getComputedStyle(input).borderColor;
  input.remove();
  return borderColor;
}

describe('validationClassName', () => {
  let previousDangerColor: string;

  beforeAll(() => {
    previousDangerColor = document.documentElement.style.getPropertyValue('--color-danger');
    document.documentElement.style.setProperty('--color-danger', dangerBorderColor);
  });

  afterAll(() => {
    document.documentElement.style.setProperty('--color-danger', previousDangerColor);
  });

  it('does not return validation state classes before the field is touched', () => {
    expect(validationClassName({ touched: false, category: 'error' } as any)).toBe('');
    expect(validationClassName({ touched: false, category: 'warning' } as any)).toBe('');
  });

  it('returns neutral validation state classes after the field is touched', () => {
    expect(
      validationClassName({ touched: true, category: 'error' } as any)
        .split(' ')
        .sort(),
    ).toEqual(['dirty', 'invalid']);
    expect(
      validationClassName({ touched: true, category: 'warning' } as any)
        .split(' ')
        .sort(),
    ).toEqual(['dirty', 'warning']);
  });

  it('does not visually invalidate untouched errors or warnings', () => {
    expect(renderedBorderColor({ touched: false, category: 'error' })).not.toBe(dangerBorderColor);
    expect(renderedBorderColor({ touched: false, category: 'warning' })).not.toBe(dangerBorderColor);
  });

  it('visually invalidates touched errors', () => {
    expect(renderedBorderColor({ touched: true, category: 'error' })).toBe(dangerBorderColor);
  });

  it('does not visually invalidate untouched highlighted errors', () => {
    expect(renderedBorderColor({ touched: false, category: 'error' }, 'highlight-pristine')).not.toBe(
      dangerBorderColor,
    );
  });
});
