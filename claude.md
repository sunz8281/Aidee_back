# 프로젝트 개요

AI 에이전트형 프로젝트 관리 서비스의 백엔드 API 서버.
녹음 파일 기반 회의 생성, AI 자동 분석(STT → LLM), 일정/메모 관리, AI 에이전트 채팅 기능을 제공한다.

API 명세는 `api.md` 파일을 참고한다. 구현 시 해당 파일의 명세를 정확히 따른다.

---

# 기술 스택

- Language: Java 17+
- Framework: Spring Boot 3.x
- Build: Gradle
- DB: MySQL
- ORM: Spring Data JPA
- Test: JUnit5 + MockMvc + Mockito

---

# 환경 설정

## DB 실행
프로젝트 루트의 `docker-compose.yml`을 사용해 DB를 실행한다.
구현 시작 전 반드시 아래 명령으로 DB를 먼저 실행한다.

```bash
docker-compose up -d
```

## 환경변수
민감한 설정값(JWT 시크릿 키, DB 비밀번호 등)은 `.env` 파일에 저장한다.
`.env` 파일은 `.gitignore`에 추가해 커밋하지 않는다.

`.env` 예시:
```
DB_URL=jdbc:mysql://localhost:3306/meeting_db
DB_USERNAME=root
DB_PASSWORD=password
JWT_SECRET=your-secret-key-here
```

`application.yml`에서 환경변수를 참조한다:
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}
```

---

# 패키지 구조

도메인 기반으로 패키지를 분리한다.

```
src/main/java/com/example/
├── project/
│   ├── ProjectController.java
│   ├── ProjectService.java
│   ├── ProjectRepository.java
│   └── dto/
├── meeting/
│   ├── MeetingController.java
│   ├── MeetingService.java
│   ├── MeetingRepository.java
│   └── dto/
├── schedule/
│   ├── ScheduleController.java
│   ├── ScheduleService.java
│   ├── ScheduleRepository.java
│   └── dto/
├── script/
│   ├── ScriptSegment.java
│   ├── ScriptRepository.java
│   └── dto/
└── agent/
    ├── AgentController.java
    └── AgentService.java
```

---

# 엔티티 설계

## Project
```
id: String (UUID, PK)
title: String (not null)
createdAt: LocalDateTime (not null)
updatedAt: LocalDateTime (not null)
```

## Meeting
```
id: String (UUID, PK)
title: String (not null)
summary: String (nullable)
memo: String (nullable)
status: MeetingStatus (PENDING, PROCESSING, DONE, FAILED)
meetingAt: LocalDateTime (not null)
recordingFile: String (nullable)
createdAt: LocalDateTime (not null)
project: Project (ManyToOne, not null)
```

## Schedule
```
id: String (UUID, PK)
title: String (not null)
startTime: LocalDateTime (not null)
endTime: LocalDateTime (not null)
allDay: boolean (not null)
sourceType: String (nullable) — "ai" | "user" | "agent"
project: Project (ManyToOne, not null)
meeting: Meeting (ManyToOne, nullable)
```

## ScriptSegment
```
id: String (UUID, PK)
contents: String (not null)
startTime: int (not null) — 초 단위
meeting: Meeting (ManyToOne, not null)
```

---

# 코드 작성 규칙

## Controller
- `@RestController`, `@RequestMapping` 사용
- 경로가 `/meetings/{id}` 처럼 상위 리소스 없이 시작하는 엔드포인트는 `@RequestMapping` prefix 없이 메서드에 full path 지정
- Request body: `@RequestBody`, Path variable: `@PathVariable`, Query param: `@RequestParam`
- SSE는 `SseEmitter` 사용

## Service
- 비즈니스 로직은 Service에서 처리
- `@Transactional` 적절히 사용
- STT/LLM 연동은 외부 서비스 호출 클래스로 분리

## DTO
- Request/Response DTO 분리
- `record` 또는 `@Getter` + `@Builder` 사용
- 필드명은 camelCase (예: `meetingAt`, `startTime`, `sourceType`)

## Entity
- `@Entity`, `@Table` 사용
- ID는 `UUID.randomUUID().toString()`으로 생성
- 연관관계는 지연 로딩(`LAZY`) 기본

## API 규칙
- 모든 ID는 String (UUID) 타입
- 날짜/시간은 ISO 8601 형식 (예: `2025-03-10T14:00:00`)
- 수정/삭제 API는 `204 No Content` 반환 (Response body 없음)
- 프로젝트 생성은 Request body 없이 서버에서 title "새 프로젝트"로 자동 생성

## SSE 엔드포인트
아래 두 엔드포인트는 `text/event-stream`으로 응답한다.
- `POST /meetings/{meetingId}/audio` — STT + LLM 분석 순차 실행
- `POST /projects/{projectId}/agent` — AI 에이전트 채팅

SSE 이벤트 형식:
```
event: {event_name}
data: {json_string}

```

## 예외 처리
- `@RestControllerAdvice`로 전역 예외 처리
- 존재하지 않는 리소스: `404 Not Found`
- 잘못된 요청: `400 Bad Request`

---

# 테스트 규칙

각 엔드포인트 구현 후 아래 두 가지를 모두 수행한다.

## 1. 코드 테스트 (JUnit5)

### 단위 테스트 (Service)
- `@ExtendWith(MockitoExtension.class)` 사용
- `@Mock`으로 Repository mocking
- `@InjectMocks`로 Service 주입
- 정상 케이스와 예외 케이스(존재하지 않는 ID 등) 모두 작성

### 통합 테스트 (Controller)
- `@WebMvcTest` 사용
- `MockMvc`로 HTTP 요청/응답 검증
- `@MockBean`으로 Service mocking
- 상태 코드, Response body 필드 검증

### 테스트 네이밍
```java
@Test
void 프로젝트_생성_성공() { ... }

@Test
void 존재하지_않는_프로젝트_조회시_404_반환() { ... }
```

## 2. curl 실제 테스트

서버를 실행한 후 curl로 실제 API를 호출해 동작을 확인한다.
서버가 실행 중이 아니면 먼저 실행한다.

```bash
# 서버 실행 (백그라운드)
./gradlew bootRun &

# 프로젝트 생성 예시
curl -X POST http://localhost:8080/projects \
  -w "\n상태코드: %{http_code}\n"

# 프로젝트 목록 조회 예시
curl http://localhost:8080/projects \
  -w "\n상태코드: %{http_code}\n"
```

curl 테스트 시 확인 사항:
- HTTP 상태 코드가 명세와 일치하는지
- Response body 구조가 명세와 일치하는지
- 204 No Content 엔드포인트는 body가 비어있는지

---

# 커밋 규칙

```
feat: 프로젝트 생성 API 구현
fix: 회의 상세 조회 NPE 수정
test: 일정 추가 서비스 단위 테스트 추가
refactor: MeetingService 메서드 분리
```

커밋 단위: 엔드포인트 1개 또는 도메인 단위로 커밋. 테스트(코드 + curl) 완료 후 커밋한다.

---

# 구현 순서

1. docker-compose로 DB 실행
2. .env 파일 생성 (JWT 시크릿 등 환경변수 설정)
3. 엔티티 + Repository 생성
4. Service 구현 + 단위 테스트
5. Controller 구현 + 통합 테스트
6. 서버 실행 후 curl 테스트
7. 커밋

도메인 순서: `project` → `meeting` → `schedule` → `agent`