# API 명세서

---

## Projects

### GET /projects
프로젝트 목록 조회

**Response**
- items: ProjectSummary[] — 프로젝트 배열 (id, name, createdAt)

---

### POST /projects
프로젝트 생성 (빈 프로젝트 자동 생성)

**Request body**: 없음 — 서버에서 title "새 프로젝트"로 자동 생성

**Response**
- id: string — 생성된 프로젝트 ID

---

### GET /projects/:projectId
프로젝트 단건 조회

**Response**
- id: string
- name: string
- meetings: MeetingSummary[] — 회의 목록 (id, title, status, meetingAt, createdAt)
- schedules: Schedule[] — 일정 목록
- createdAt: string

---

### PATCH /projects/:projectId/title
프로젝트 타이틀 수정

**Request body**
- name: string (required) — 변경할 프로젝트 이름

**Response**: 204 No Content

---

### DELETE /projects/:projectId
프로젝트 삭제

**Response**: 204 No Content

---

## Meetings

### GET /projects/:projectId/meetings
회의 목록 조회

**Response**
- items: MeetingSummary[] — 회의 배열 (id, title, status, meetingAt, createdAt)

---

### POST /projects/:projectId/meetings
회의 생성 (빈 데이터)

**Request body**
- title: string (optional) — 회의 제목 (미입력 시 날짜 자동 생성)
- meetingAt: string (optional) — 회의 진행 일시 (ISO 8601)

**Response**
- id: string
- title: string
- meetingAt: string
- createdAt: string

---

### POST /meetings/:meetingId/audio
녹음 파일 업로드 및 AI 분석 실행 (SSE)

**Request**: multipart/form-data
- audioFile: file (required) — 녹음 파일 (mp3, m4a, wav)

**SSE Response events**
- event: stt_progress — data: { progress: number } — STT 변환 진행률 0~100
- event: stt_done — data: { script: string } — STT 완료, 스크립트 전달
- event: ai_progress — data: { step: string } — LLM 분석 단계 ("요약 중", "일정 추출 중" 등)
- event: done — data: { meetingId: string } — 전체 분석 완료

**Note**: 업로드 즉시 STT → LLM 분석이 순차 실행됨. 분석 완료 후 회의 상세 조회로 결과 확인 가능.

---

### GET /meetings/:meetingId
회의 상세 조회

**Response**
- id: string
- title: string
- meetingAt: string
- status: string — "pending" | "processing" | "done" | "failed"
- summary: string — AI 요약
- memo: string — 회의 메모
- scripts: ScriptSegment[] — 스크립트 세그먼트 배열 (startTime: int, contents: string)
- schedules: Schedule[] — 회의에서 추출된 일정
- audioUrl: string — 원본 녹음 파일 URL
- createdAt: string

---

### PATCH /meetings/:meetingId
회의 수정 (타이틀 / 진행 일시)

**Request body**
- title: string (optional) — 변경할 회의 제목
- meetingAt: string (optional) — 변경할 회의 진행 일시

**Response**: 204 No Content

---

### DELETE /meetings/:meetingId
회의 삭제

**Response**: 204 No Content

---

## Memos

### GET /projects/:projectId/memos
메모 전체 조회

**Response**
- items: Memo[] — 메모 배열 (meetingId, meetingTitle, memo)

---

### PATCH /meetings/:meetingId/memo
메모 수정 (디바운싱 + onBlur 자동 저장)

**Request body**
- memo: string (required) — 변경할 메모 내용

**Response**: 204 No Content

---

## Schedules

### GET /projects/:projectId/schedules
일정 목록 조회

**Query params**
- year: number (required) — 조회 연도 (예: 2025)
- month: number (required) — 조회 월 (1~12)

**Response**
- items: Schedule[]

---

### POST /projects/:projectId/schedules
일정 추가

**Request body**
- title: string (required)
- startTime: string (required) — 시작 일시 (ISO 8601)
- endTime: string (required) — 종료 일시 (ISO 8601)
- allDay: boolean (required) — 하루 종일 여부
- sourceType: string (optional) — "ai" | "user" | "agent"
- sourceMeetingId: string (optional) — AI 추출 시 원본 회의 ID

**Response**
- id: string
- title: string
- startTime: string
- endTime: string
- createdAt: string

---

### PUT /schedules/:scheduleId
일정 수정

**Request body**
- title: string (optional)
- startTime: string (optional)
- endTime: string (optional)
- allDay: boolean (optional)

**Response**
- id: string
- title: string

---

### DELETE /schedules/:scheduleId
일정 삭제

**Response**: 204 No Content

---

## Agent

### POST /projects/:projectId/agent
채팅 메시지 전송 (SSE)

**Query params**
- meetingId: string (optional) — 특정 회의 기반 채팅 시 전달 (없으면 프로젝트 전체 기반)

**Request body**
- message: string (required) — 사용자 입력 메시지
- history: Message[] (optional) — 이전 대화 내역 (로컬 스토리지에서 전달)

**Message 객체**
- role: string — "user" | "assistant"
- content: string — 메시지 내용

**SSE Response events**
- event: delta — data: { text: string } — 스트리밍 텍스트 청크
- event: action — data: { type, payload } — 수행 액션
- event: done — data: {} — 스트리밍 종료

**Note**
- meetingId 없음 → 프로젝트 전체 기반
- meetingId 있음 → 해당 회의 기반
- 대화 기록은 로컬 스토리지에 저장되며 매 요청마다 history로 전달
- action type: "schedule_added" | "schedule_updated" | "schedule_deleted" | "memo_updated"
