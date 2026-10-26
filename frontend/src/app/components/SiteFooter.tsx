import Link from 'next/link';

const links = [
  { label: '洛谷', href: 'https://www.luogu.com.cn/' },
  { label: '牛客', href: 'https://ac.nowcoder.com/' },
  { label: 'AtCoder', href: 'https://atcoder.jp/' },
  { label: 'Codeforces', href: 'https://codeforces.com/' },
  { label: 'VJudge', href: 'https://vjudge.net/' },
];

export default function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <div className="site-footer-main">
          <div className="site-footer-left">
            <div className="site-footer-intro">
              <Link href="/" className="site-footer-brand" aria-label="PAA 竞赛管理平台首页">
                <img src="/paa-logo.svg" alt="PAA 竞赛管理平台" />
              </Link>
              <span>编程、算法竞赛与工程实践的学习平台</span>
            </div>
            <nav className="site-footer-links" aria-label="学习平台">
              <div className="site-footer-link-group">
                <h2 className="site-footer-link-title">学习平台</h2>
                <div className="site-footer-link-items">
                  {links.map(({ label, href }) => (
                    <a key={href} href={href} target="_blank" rel="noreferrer">{label}</a>
                  ))}
                </div>
              </div>
            </nav>
          </div>
        </div>
        <div className="site-footer-meta"><span>PAA 竞赛管理平台</span></div>
      </div>
    </footer>
  );
}
