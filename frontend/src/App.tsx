import { Link, Route, Routes } from "react-router-dom";
import { ProfilePage } from "./pages/ProfilePage";
import { ResultsPage } from "./pages/ResultsPage";
import { BenefitsPage } from "./pages/BenefitsPage";
import { DetailPage } from "./pages/DetailPage";

export default function App() {
  return (
    <>
      <header className="site-header">
        <div className="wrap nav">
          <Link className="brand" to="/">
            실버헬퍼<span>+</span>
          </Link>
          <nav aria-label="주 메뉴">
            <Link to="/">맞춤 혜택 찾기</Link>
            <Link to="/benefits">전체 혜택</Link>
          </nav>
        </div>
      </header>
      <main className="wrap">
        <Routes>
          <Route path="/" element={<ProfilePage />} />
          <Route path="/results" element={<ResultsPage />} />
          <Route path="/benefits" element={<BenefitsPage />} />
          <Route path="/benefits/:id" element={<DetailPage />} />
        </Routes>
      </main>
      <footer className="footer">
        <div className="wrap">
          실버헬퍼 · 혜택 자격과 신청 방법은 공식 출처에서 확인하세요.
        </div>
      </footer>
    </>
  );
}
