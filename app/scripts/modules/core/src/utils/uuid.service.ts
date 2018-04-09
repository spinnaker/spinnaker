export class UUIDGenerator {
  private static getRandom(max: number) {
    return Math.random() * max;
  }

  public static generateUuid(): string {
    // source: https://github.com/daniellmb/angular-uuid-service/blob/master/angular-uuid-service.js
    let uuid = '';
    for (let i = 0; i < 36; i++) {
      switch (i) {
        case 14:
          uuid += '4';
          break;
        case 19:
          uuid += '89ab'.charAt(UUIDGenerator.getRandom(4));
          break;
        case 8:
        case 13:
        case 18:
        case 23:
          uuid += '-';
          break;
        default:
          uuid += '0123456789abcdef'.charAt(UUIDGenerator.getRandom(16));
      }
    }

    return uuid;
  }
}
