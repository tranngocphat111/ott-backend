const Snowflake = require("snowflake-id").default;

const snowflake = new Snowflake({
  mid: 1,
  offset: (2024 - 1970) * 31536000 * 1000,
});

exports.generateId = () => {
  return snowflake.generate().toString();
};
