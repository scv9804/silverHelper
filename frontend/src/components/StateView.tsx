export function StateView({
  loading,
  error,
  empty,
  emptyMessage,
}: {
  loading: boolean;
  error: Error | null;
  empty?: boolean;
  emptyMessage?: string;
}) {
  if (loading)
    return (
      <p role="status" className="notice">
        정보를 불러오는 중입니다…
      </p>
    );
  if (error)
    return (
      <p role="alert" className="notice error">
        {error.message}
      </p>
    );
  if (empty)
    return (
      <p className="notice">
        {emptyMessage || "해당하는 혜택이 없습니다. 조건을 바꿔 다시 찾아보세요."}
      </p>
    );
  return null;
}
