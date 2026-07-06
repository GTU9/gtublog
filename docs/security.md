# 보안 메모

## 브라우저 / API 경계

- 운영 환경은 reverse proxy를 통한 same-origin browser access를 전제로 설계됩니다.
- 개발과 staging은 명시적인 origin allowlist만 사용합니다.
- access token은 브라우저 메모리에만 유지됩니다.
- refresh는 host-only `HttpOnly` cookie와 double-submit CSRF 보호를 사용합니다.

## 응답 하드닝

현재 backend 응답에는 다음 헤더가 포함됩니다.

- `Content-Security-Policy`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `X-Frame-Options: DENY`
- `Permissions-Policy`
- 보안 전송 환경에서의 `Strict-Transport-Security`

이 헤더는 인증 및 관리자 API 응답을 포함한 Spring surface 전체에 적용됩니다. Next.js의 public / administrator HTML surface도 동일한 브라우저 기준선을 따르며, CSP, HSTS, frame denial, MIME sniffing 방지, referrer policy, 제한된 permissions policy를 함께 적용합니다.

수동 작성 글과 자동 생성 글의 HTML은 Spring trust boundary에서 sanitize 됩니다. allowlist는 일반적인 article 구조는 유지하되 script, event handler, embedded object, HTTP(S)가 아닌 image URL을 제거하며, link에는 `noopener noreferrer` 방어를 적용합니다.

## 관측성과 시크릿 처리

- Prometheus metric에는 token material, secret, source body, generated content payload가 포함되면 안 됩니다.
- 관리자 진단 화면은 count와 hold reason만 노출해야 하며, raw credential과 worker token은 제외합니다.
- 감사 로그는 login success, refresh rotation, replay detection, logout 같은 보안 관련 이벤트의 system of record로 유지됩니다.
- Pull request에서는 Node dependency audit, repository-history secret scan, CodeQL, lockfile vulnerability scan, hardened worker image scan을 실행합니다. workflow action은 immutable commit에 pin하고, 기본 저장소 권한은 read-only로 유지합니다.
