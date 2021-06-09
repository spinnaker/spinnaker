const selfsigned = require('selfsigned');

/**
 * Generate certificates using selfsigned module.
 *
 * Borrowed from Webpack:
 * https://github.com/webpack/webpack-dev-server/blob/ca36239cd845115084ca1ec8c8ff3ae10d414e86/lib/utils/createCertificate.js
 */
function createCertificate(attributes) {
  return selfsigned.generate(attributes, {
    algorithm: 'sha256',
    days: 30,
    keySize: 2048,
    extensions: [
      {
        name: 'keyUsage',
        keyCertSign: true,
        digitalSignature: true,
        nonRepudiation: true,
        keyEncipherment: true,
        dataEncipherment: true,
      },
      {
        name: 'extKeyUsage',
        serverAuth: true,
        clientAuth: true,
        codeSigning: true,
        timeStamping: true,
      },
      {
        name: 'subjectAltName',
        altNames: [
          {
            // type 2 is DNS
            type: 2,
            value: 'localhost',
          },
          {
            type: 2,
            value: 'localhost.localdomain',
          },
          {
            type: 2,
            value: 'lvh.me',
          },
          {
            type: 2,
            value: '*.lvh.me',
          },
          {
            type: 2,
            value: '[::1]',
          },
          {
            // type 7 is IP
            type: 7,
            ip: '127.0.0.1',
          },
          {
            type: 7,
            ip: 'fe80::1',
          },
        ],
      },
    ],
  });
}

const certs = createCertificate([{ name: 'commonName', value: 'localhost' }]);
const combined = certs.private + certs.cert;

module.exports = {
  key: combined,
  cert: combined,
};
