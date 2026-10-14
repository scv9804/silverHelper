import { useQuery } from "@tanstack/react-query";
import { api, type Region } from "../api";
export function RegionFields({
  ctpvName,
  sggName,
  onChange,
}: {
  ctpvName: string;
  sggName: string | null;
  onChange: (patch: { ctpvName?: string; sggName?: string | null }) => void;
}) {
  const { data } = useQuery({
    queryKey: ["regions"],
    queryFn: () => api<Region[]>("/api/regions"),
    staleTime: Infinity,
  });
  const cities = [...new Set(data?.map((r) => r.ctpvName) || [])];
  return (
    <div className="field-row">
      <label>
        거주 시도
        <select
          required
          value={ctpvName}
          onChange={(event) =>
            onChange({ ctpvName: event.target.value, sggName: null })
          }
        >
          <option value="">시도를 선택하세요</option>
          {cities.map((city) => (
            <option key={city}>{city}</option>
          ))}
        </select>
      </label>
      <label>
        시군구
        <select
          value={sggName || ""}
          onChange={(event) =>
            onChange({ sggName: event.target.value || null })
          }
        >
          <option value="">전체 / 모름</option>
          {data
            ?.find((region) => region.ctpvName === ctpvName)
            ?.sggNames.map((name) => (
              <option key={name}>{name}</option>
            ))}
        </select>
      </label>
    </div>
  );
}
