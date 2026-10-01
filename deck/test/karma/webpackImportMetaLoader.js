const regex = /import\.meta\.env/g;

module.exports = function (source) {
  return source.replace(regex, 'process.env');
};
