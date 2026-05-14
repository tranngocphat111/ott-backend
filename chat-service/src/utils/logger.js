/**
 * Logger Utility
 * Simple logging for chat service
 */

class Logger {
  log(message) {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] ${message}`);
  }

  info(message) {
    this.log(`ℹ️  ${message}`);
  }

  error(message, error) {
    const timestamp = new Date().toISOString();
    const errorMsg = error ? error.stack || error.message : '';
    console.error(`[ERROR] [${timestamp}] ${message}`);
    if (errorMsg) console.error(errorMsg);
  }

  warn(message) {
    this.log(`⚠️  ${message}`);
  }

  debug(message) {
    this.log(`🐛 ${message}`);
  }
}

module.exports = new Logger();
