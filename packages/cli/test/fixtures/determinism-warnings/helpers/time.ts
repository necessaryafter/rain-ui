export function builtAt(): string {
  return Date.now() > 0 ? "now" : "never";
}

export function builtYear(): string {
  return new Date().getFullYear() > 0 ? "year" : "never";
}
