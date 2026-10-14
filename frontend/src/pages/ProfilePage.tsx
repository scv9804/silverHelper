import { Link, useNavigate } from "react-router-dom";
import { useProfile } from "../store";
import { RegionFields } from "../components/RegionFields";
import { ThreeWay } from "../components/ThreeWay";
import { useAuth } from "../auth-context";
export function ProfilePage() {
  const { profile, update, editing } = useProfile();
  const { user } = useAuth();
  const navigate = useNavigate();
  // 비로그인 상태에서 undefined === undefined가 참이 되지 않도록 둘 다 있을 때만 비교한다
  const editingThisUser = editing != null && user != null && editing.userId === user.id;
  return (
    <section>
      <div className="hero">
        <span className="eyebrow">부모님을 위한 복지 찾기</span>
        <h1>
          우리 부모님께 맞는
          <br />
          혜택을 찾아보세요
        </h1>
        <p>알고 있는 정보만 입력해도 됩니다. 모르는 항목은 그대로 두세요.</p>
      </div>
      <form
        className="panel"
        onSubmit={(event) => {
          event.preventDefault();
          navigate(editingThisUser ? "/me" : "/results");
        }}
      >
        <h2>{editingThisUser ? `${editing?.name} 정보 수정` : "부모님 정보"}</h2>
        <p className="muted">{user ? <>현재 탭에서 입력을 유지합니다. 계정에 보관하려면 <Link to="/me">마이페이지에서 프로필을 저장</Link>하세요.</> : "입력 내용은 이 탭에 임시 저장되며 탭을 닫거나 로그아웃하면 지워집니다."}</p>
        <label>
          나이
          <input
            type="number"
            required
            min="0"
            max="120"
            value={profile.age || ""}
            placeholder="나이를 입력하세요"
            onChange={(event) => update({ age: Number(event.target.value) })}
          />
        </label>
        <RegionFields
          ctpvName={profile.ctpvName}
          sggName={profile.sggName}
          onChange={update}
        />
        <div className="field-row">
          <label>
            장기요양 등급
            <select
              value={profile.ltcGrade ?? (profile.hasNoLtcGrade ? "none" : "")}
              onChange={(event) => {
                const v = event.target.value;
                update({
                  ltcGrade: v && v !== "none" ? Number(v) : null,
                  hasNoLtcGrade: v === "none" ? true : null,
                });
              }}
            >
              <option value="">모름</option>
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n} value={n}>
                  {n}등급
                </option>
              ))}
              <option value="6">인지지원등급</option>
              <option value="none">없음</option>
            </select>
          </label>
          <label>
            중위소득 비율 (%)
            <input
              type="number"
              min="0"
              max="1000"
              placeholder="모름"
              value={profile.incomeMedianPct ?? ""}
              onChange={(event) =>
                update({
                  incomeMedianPct:
                    event.target.value === ""
                      ? null
                      : Number(event.target.value),
                })
              }
            />
          </label>
        </div>
        <div className="questions">
          {(
            [
              ["기초생활수급", "basicLivelihood"],
              ["차상위계층", "nearPoverty"],
              ["기초연금 수급", "basicPension"],
              ["등록 장애", "disability"],
              ["보훈 대상", "veteran"],
              ["독거", "livingAlone"],
            ] as const
          ).map(([label, key]) => (
            <ThreeWay
              key={key}
              label={label}
              value={profile[key]}
              onChange={(value) => update({ [key]: value })}
            />
          ))}
        </div>
        <button className="primary" type="submit">
          {editingThisUser ? "마이페이지에서 변경 저장 →" : "맞는 혜택 찾기 →"}
        </button>
      </form>
    </section>
  );
}
