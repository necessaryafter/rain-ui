export function Random() {
  return <text value={Math.random() > 2 ? "never" : "always"} />;
}
