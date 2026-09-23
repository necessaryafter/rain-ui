export default function Title({ value }: { value: string | { $bind: string } }) {
  return <text value={value} />;
}
