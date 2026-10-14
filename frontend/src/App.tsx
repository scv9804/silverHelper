import { Link, Route, Routes } from "react-router-dom";
import { useState } from "react";
import { ProfilePage } from "./pages/ProfilePage";
import { ResultsPage } from "./pages/ResultsPage";
import { BenefitsPage } from "./pages/BenefitsPage";
import { DetailPage } from "./pages/DetailPage";
import { LoginPage, AuthCallbackPage } from "./pages/LoginPage";
import { MyPage } from "./pages/MyPage";
import { AdminReviewsPage } from "./pages/AdminReviewsPage";
import { ChatPage } from "./pages/ChatPage";
import { NotificationPage } from "./pages/NotificationPage";
import { useAuth } from "./auth-context";

export default function App() {
  const { user, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <>
      <header className="site-header">
        <div className="wrap nav">
          <Link className="brand" to="/">
            실버헬퍼<span>+</span>
          </Link>
          <button className="menu-toggle secondary" type="button" aria-expanded={menuOpen} aria-controls="main-nav" onClick={() => setMenuOpen(!menuOpen)}>메뉴</button>
          <nav id="main-nav" className={menuOpen ? "open" : ""} aria-label="주 메뉴" onClick={() => setMenuOpen(false)}>
            <Link to="/">맞춤 혜택 찾기</Link>
            <Link to="/benefits">전체 혜택</Link>
            <Link to="/chat">챗봇</Link>
            {user?.role === "ADMIN" && <Link to="/admin/reviews">관리자 검토</Link>}
            {user ? <><Link to="/me">마이페이지</Link><button className="nav-button" onClick={() => void logout()}>로그아웃</button></> : <Link to="/login">로그인</Link>}
          </nav>
        </div>
      </header>
      <main className="wrap">
        <Routes>
          <Route path="/" element={<ProfilePage />} />
          <Route path="/results" element={<ResultsPage />} />
          <Route path="/benefits" element={<BenefitsPage />} />
          <Route path="/benefits/:id" element={<DetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/me/notifications" element={<NotificationPage />} />
          <Route path="/admin/reviews" element={<AdminReviewsPage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="*" element={<div className="panel"><h1>페이지를 찾을 수 없습니다</h1><p>주소를 확인하거나 첫 화면에서 다시 시작해 주세요.</p><Link to="/">첫 화면으로</Link></div>} />
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
