export function ThreeWay({
  label,
  value,
  onChange,
}: {
  label: string;
  value: boolean | null;
  onChange: (value: boolean | null) => void;
}) {
  return (
    <fieldset>
      <legend>{label}</legend>
      <div className="choices">
        {(
          [
            { label: "예", value: true },
            { label: "아니오", value: false },
            { label: "모름", value: null },
          ] as const
        ).map((choice) => (
          <label key={choice.label}>
            <input
              type="radio"
              name={label}
              checked={value === choice.value}
              onChange={() => onChange(choice.value)}
            />
            {choice.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
