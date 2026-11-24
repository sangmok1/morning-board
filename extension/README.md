# 아침보드 (크롬 확장 · 팝업)

안드로이드 **아침보드**를 크롬 확장 **팝업**으로 포팅.
툴바의 확장 아이콘을 누르면 작은 창으로 날씨·옷차림·버스 도착이 뜬다.
Android 버전과 **같은 디자인**(픽셀아트·글래스 카드·날씨 배경 애니메이션) 유지.

## 설치 (로컬, 개발자 모드)
1. 크롬에서 `chrome://extensions` 접속
2. 우측 상단 **개발자 모드** 켜기
3. **압축해제된 확장 프로그램을 로드합니다** → 이 `extension/` 폴더 선택
4. 툴바의 퍼즐 아이콘 → 아침보드를 **고정(📌)**
5. 아이콘 클릭 → 팝업 → ⚙ 설정에서 위치·버스 정류장 등록

## 설정
- **날씨 위치**: 동네 이름 검색(예: "분당구 정자동"). 기본은 Nominatim(OSM, 키 불필요), 더 정확하게 하려면 카카오 REST 키 입력(선택).
- **버스**: [공공데이터포털](https://data.go.kr) 인증키 필요
  - 서울: 서울특별시_정류소정보조회 서비스
  - 경기: 경기도_버스도착정보 조회 + 경기도_정류소 조회
  - 한 키로 둘 다 됨. 설정에서 정류장 번호(경기는 이름도 가능) 조회 후 볼 버스 체크.

## 구조
- `manifest.json` — MV3, `action.default_popup`
- `popup.html` / `css/popup.css` — 460×600 컴팩트 레이아웃
- `js/weather.js` — Open-Meteo (키 불필요)
- `js/bus.js` — 서울/경기 버스 + 지명검색(카카오→Nominatim→Open-Meteo), XML은 DOMParser
- `js/pixel.js` — 픽셀 스프라이트 + canvas 렌더
- `js/backdrop.js` — 날씨별 배경 애니메이션
- `js/board.js` — 설정 저장(chrome.storage.local) + ETA 포맷
- `js/app.js` — 상태·렌더·틱·설정 패널

## 메모
- 설정은 `chrome.storage.local` 에 저장 (확장이 아닌 일반 페이지에서 열면 localStorage 폴백).
- 서울 버스 API(ws.bus.go.kr)는 http 전용이라 `host_permissions` 에 http/https 둘 다 선언.
