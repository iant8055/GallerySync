type LogLevel = 'info' | 'warn' | 'error' | 'debug';

function log(level: LogLevel, message: string, meta?: unknown): void {
  const ts = new Date().toISOString();
  const line = meta !== undefined
    ? `[${ts}] [${level.toUpperCase()}] ${message} ${JSON.stringify(meta)}`
    : `[${ts}] [${level.toUpperCase()}] ${message}`;

  if (level === 'error') {
    process.stderr.write(line + '\n');
  } else {
    process.stdout.write(line + '\n');
  }
}

export const Logger = {
  info:  (msg: string, meta?: unknown) => log('info',  msg, meta),
  warn:  (msg: string, meta?: unknown) => log('warn',  msg, meta),
  error: (msg: string, meta?: unknown) => log('error', msg, meta),
  debug: (msg: string, meta?: unknown) => log('debug', msg, meta),
};
