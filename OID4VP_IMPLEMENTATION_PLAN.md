# OID4VP 통합 구현 작업계획서

> 작성일: 2026-03-16
> 담당: Verifier-Server & Demo-Server 백엔드 개발
> 기반 문서: `PROTOCOL_ROUTING_DESIGN.md` (v9.0)

---

## 전제 조건

- `PROTOCOL_ROUTING_DESIGN.md`의 아키텍처 설계 및 아젠다 결정사항은 확정된 것으로 간주
- `did-oid4vp-sdk-server` 모듈 (45개 Java 클래스)은 이미 개발 완료 상태
- Wallet(앱)은 서버 개발 완료 후 별도 팀이 진행. 프로토타입 단계에서는 Demo 화면이 Wallet 역할을 시뮬레이션
- Admin UI 확장은 프로토타입 이후 진행

---

## Phase 0: DB 스키마 설계 확정

> 프로토타입/프로덕션 공통. 모든 구현의 기반이 되므로 최우선 확정.

### 0-1. `policy` 테이블 변경 (기존 테이블 확장)

```sql
-- Liquibase: set.3/protocol-add_protocol_type.xml
ALTER TABLE policy ADD COLUMN protocol_type VARCHAR(20) NOT NULL DEFAULT 'DID_VP';
ALTER TABLE policy ADD COLUMN scope VARCHAR(100);

-- OID4VP Policy는 payload_id, policy_profile_id가 불필요하므로 nullable로 변경
ALTER TABLE policy ALTER COLUMN payload_id DROP NOT NULL;
ALTER TABLE policy ALTER COLUMN policy_profile_id DROP NOT NULL;
```

| 컬럼 | 타입 | 기본값 | 비고 |
|------|------|--------|------|
| `protocol_type` | VARCHAR(20) NOT NULL | `'DID_VP'` | `DID_VP` / `OID4VP` |
| `scope` | VARCHAR(100) NULL | - | OID4VP일 때 `dcql_scope_mapping.scope` 참조 |

> **중요**: OID4VP Policy는 Filter→Process→Profile 4단계가 아닌 Scope→Policy 2단계 구조.
> `payload_id`, `policy_profile_id`는 DID VP 전용이므로 OID4VP Policy 생성 시 NULL 허용 필요.

### 0-2. `oid4vp_config` 테이블 (신규)

```sql
-- Liquibase: set.3/protocol-create_oid4vp_config.xml
CREATE TABLE oid4vp_config (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(50)   NOT NULL DEFAULT 'OID4VP',
    config      TEXT          NOT NULL,   -- OID4VPConfig JSON 전체
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

-- 서버 전체에 1개 설정만 존재하도록 UNIQUE 제약
CREATE UNIQUE INDEX uq_oid4vp_config_type ON oid4vp_config(type);
```

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `id` | BIGSERIAL PK | |
| `type` | VARCHAR(50) UNIQUE | `'OID4VP'` (단일 레코드) |
| `config` | TEXT | OID4VPConfig JSON (baseUrl, clientId, session, endpoints, clientMetadata, crypto) |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**config JSON 구조** (SDK `OID4VPConfig` 기반):
```json
{
  "baseUrl": "https://verifier.example.com",
  "clientName": "OpenDID Verifier",
  "invocationScheme": "openid4vp://",
  "clientId": {
    "scheme": "redirect_uri",
    "value": "https://verifier.example.com/oid4vp/response"
  },
  "session": { "sessionTtl": 300000 },
  "endpoints": {
    "response": "/oid4vp/response",
    "request": "/oid4vp/request"
  },
  "clientMetadata": {
    "vpFormatsSupported": { "jwt_vp_json": { "alg_values_supported": ["ES256"] } }
  },
  "crypto": {
    "vpTokenEncryptionKey": null
  }
}
```

### 0-3. `dcql_scope_mapping` 테이블 (신규)

```sql
-- Liquibase: set.3/protocol-create_dcql_scope_mapping.xml
CREATE TABLE dcql_scope_mapping (
    id           BIGSERIAL PRIMARY KEY,
    scope        VARCHAR(100)  NOT NULL,
    dcql_query   TEXT          NOT NULL,   -- DCQLQuery JSON
    description  VARCHAR(500),
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP
);

CREATE UNIQUE INDEX uq_dcql_scope ON dcql_scope_mapping(scope);
```

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `id` | BIGSERIAL PK | |
| `scope` | VARCHAR(100) UNIQUE | scope 식별자 (예: `id_card_verification`) |
| `dcql_query` | TEXT | DCQLQuery JSON |
| `description` | VARCHAR(500) | |
| `enabled` | BOOLEAN DEFAULT TRUE | |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**dcql_query JSON 구조** (SDK `DCQLQuery` 기반):
```json
{
  "credentials": [{
    "id": "id_card",
    "format": "jwt_vc_json",
    "meta": { "vct": "NationalIdCredential" },
    "claims": [
      { "id": "name_claim", "path": ["name"] },
      { "id": "birth_claim", "path": ["birthDate"] }
    ]
  }]
}
```

### 0-4. `oid4vp_session_mapping` 테이블 (신규)

```sql
-- Liquibase: set.3/protocol-create_oid4vp_session_mapping.xml
CREATE TABLE oid4vp_session_mapping (
    id                     BIGSERIAL PRIMARY KEY,
    tx_id                  VARCHAR(40)   NOT NULL,
    oid4vp_transaction_id  VARCHAR(100)  NOT NULL,
    oid4vp_request_id      VARCHAR(100)  NOT NULL,
    state                  VARCHAR(100)  NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oid4vp_mapping_tx_id ON oid4vp_session_mapping(tx_id);
CREATE INDEX idx_oid4vp_mapping_state ON oid4vp_session_mapping(state);
CREATE INDEX idx_oid4vp_mapping_request_id ON oid4vp_session_mapping(oid4vp_request_id);
```

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `tx_id` | VARCHAR(40) | 통합 Transaction ID 참조 |
| `oid4vp_transaction_id` | VARCHAR(100) | OID4VP SDK 내부 세션 ID |
| `oid4vp_request_id` | VARCHAR(100) | Authorization Request ID (JAR 조회용) |
| `state` | VARCHAR(100) | OAuth2 state 파라미터 (CSRF 방지) |

### 0-5. ER 다이어그램 (변경 후)

```
┌──────────────────────┐       ┌─────────────────────┐
│      policy          │       │   policy_profile     │
├──────────────────────┤       ├─────────────────────┤
│ id (PK)              │  ┌──> │ policy_profile_id    │
│ policy_id (UNIQUE)   │  │    │ ...                  │
│ policy_title         │  │    └─────────────────────┘
│ policy_type (VP|ZKP) │  │
│ protocol_type (NEW)  │──┤    ┌─────────────────────┐
│ payload_id (FK,NULL) │──┼──> │   payload            │
│ policy_profile_id    │──┘    └─────────────────────┘
│   (FK, NULLABLE)     │
│ scope (NEW, NULL)    │─────> ┌─────────────────────┐
│ created_at           │       │ dcql_scope_mapping   │
│ updated_at           │       ├─────────────────────┤
└──────────────────────┘       │ id (PK)              │
                               │ scope (UNIQUE)       │
┌──────────────────────┐       │ dcql_query (JSON)    │
│   oid4vp_config      │       │ description          │
├──────────────────────┤       │ enabled              │
│ id (PK)              │       │ created_at           │
│ type (UNIQUE)        │       │ updated_at           │
│ config (JSON)        │       └─────────────────────┘
│ created_at           │
│ updated_at           │       ┌──────────────────────────┐
└──────────────────────┘       │  oid4vp_session_mapping   │
                               ├──────────────────────────┤
┌──────────────────────┐       │ id (PK)                  │
│    transaction       │<──────│ tx_id (FK)               │
├──────────────────────┤       │ oid4vp_transaction_id    │
│ id (PK)              │       │ oid4vp_request_id        │
│ tx_id                │       │ state                    │
│ type (VP|OID4VP)     │       │ created_at               │
│ status               │       └──────────────────────────┘
│ expired_at           │
└──────────────────────┘
```

---

## Phase 1: 기반 — SDK 통합 + Domain

> Phase 0의 DB 설계를 코드로 구현하고, OID4VP SDK를 빌드에 통합하는 단계.

### 1-1. Liquibase 마이그레이션 작성

| 파일 | 내용 |
|------|------|
| `set.3/protocol-add_protocol_type.xml` | policy 테이블에 `protocol_type`, `scope` 추가. `payload_id`, `policy_profile_id` nullable 변경 |
| `set.3/protocol-create_oid4vp_config.xml` | `oid4vp_config` 테이블 생성 |
| `set.3/protocol-create_dcql_scope_mapping.xml` | `dcql_scope_mapping` 테이블 생성 |
| `set.3/protocol-create_oid4vp_session_mapping.xml` | `oid4vp_session_mapping` 테이블 생성 |
| `set_master.xml` 수정 | set.3 include 추가 |

### 1-2. Domain 엔티티 / Enum

| 파일 | 내용 |
|------|------|
| `base/db/constant/ProtocolType.java` | Enum: `DID_VP`, `OID4VP` |
| `base/db/domain/Policy.java` 수정 | `protocolType`, `scope` 필드 추가 |
| `base/db/domain/Oid4vpConfig.java` | Entity: type, config(JSON) |
| `base/db/domain/DcqlScopeMapping.java` | Entity: scope, dcqlQuery, description, enabled |
| `base/db/domain/Oid4vpSessionMapping.java` | Entity: txId, oid4vpTransactionId, requestId, state |
| `base/db/repository/Oid4vpConfigRepository.java` | `findByType()` |
| `base/db/repository/DcqlScopeMappingRepository.java` | `findByScope()`, `findAllByEnabledTrue()` |
| `base/db/repository/Oid4vpSessionMappingRepository.java` | `findByTxId()`, `findByState()`, `findByOid4vpRequestId()` |
| `base/db/constant/TransactionType.java` 수정 | `OID4VP` 값 추가 |

### 1-3. OID4VP SDK 모듈 통합

| 파일 | 변경 내용 |
|------|---------|
| `settings.gradle` | `includeBuild('did-oid4vp-sdk-server')` 추가 |
| `build.gradle` | `implementation 'org.omnione.did:did-oid4vp-sdk-server'` 의존성 추가 |
| `did-oid4vp-sdk-server/build.gradle` | fat JAR → thin JAR 전환 |

### 1-4. 초기 데이터 (프로토타입용)

| 파일 | 내용 |
|------|------|
| `data-prototype.sql` | 더미 Policy 2개 (DID_VP 1개, OID4VP 1개), OID4VP Config 1개, DCQL Scope Mapping 1개 |

### 산출물 확인

- [ ] `./gradlew bootJar -DskipFrontendBuild=true` 성공
- [ ] H2 In-Memory에서 테이블 생성 확인
- [ ] 초기 데이터 삽입 확인

---

## Phase 2: 통합 진입점 — initiate + 프로토콜 라우팅

> 핵심 레이어. Policy 기반으로 프로토콜을 결정하고 적절한 Handler에 위임.

### 2-1. Protocol 레이어 (신규)

| 파일 | 역할 |
|------|------|
| `protocol/handler/ProtocolHandler.java` | 인터페이스: `getProtocolType()`, `initiate(InitiateRequest): InitiateResponse` |
| `protocol/handler/DidVpProtocolHandler.java` | 기존 `VpOfferApplicationService.requestVpOfferbyQR()` 래핑 |
| `protocol/handler/Oid4vpProtocolHandler.java` | OID4VP SDK `InitiationService.initiateVerification()` 래핑 + 세션 매핑 저장 |
| `protocol/registry/ProtocolRegistry.java` | `Map<ProtocolType, ProtocolHandler>` 팩토리 |
| `protocol/resolver/PolicyProtocolResolver.java` | Policy 조회 → `ProtocolType` 반환 |
| `protocol/orchestrator/VerificationOrchestrator.java` | resolver → registry → handler 체인 |

### 2-2. Controller / DTO

| 파일 | 역할 |
|------|------|
| `protocol/api/UnifiedVerificationController.java` | `POST /v2/initiate` |
| `protocol/api/dto/InitiateRequest.java` | `policyId: String` |
| `protocol/api/dto/InitiateResponse.java` | `protocol`, `sessionId`, `payload`(DID VP), `authorizationRequest`(OID4VP), `nextEndpoints` |

### 2-3. 설정

| 파일 | 역할 |
|------|------|
| `protocol/config/OID4VPIntegrationConfig.java` | OID4VPConfig Bean, 키페어 Bean |
| `protocol/config/ProtocolLayerConfig.java` | ProtocolRegistry Bean |

### 산출물 확인

- [ ] `POST /v2/initiate {policyId: "policy-didvp-demo"}` → DID VP 응답
- [ ] `POST /v2/initiate {policyId: "policy-oid4vp-demo"}` → OID4VP 응답 (authorizationRequest 포함)
- [ ] 잘못된 policyId → 에러 응답

---

## Phase 3: 후속 플로우 — 프로토콜별 엔드포인트

> Wallet(또는 시뮬레이션)이 호출하는 프로토콜별 후속 API.

### 3-1. OID4VP 엔드포인트 (신규)

| 파일 | 엔드포인트 |
|------|-----------|
| `protocol/api/OID4VPController.java` | `GET /oid4vp/request/{requestId}` — JAR 조회 |
| | `POST /oid4vp/response` — VP Token 제출 |

동작:
1. `GET /oid4vp/request/{requestId}` → SDK `AuthorizationService.getAuthorizationRequest()` 래핑 → JWT 반환
2. `POST /oid4vp/response` → SDK `AuthorizationService.receiveResponse()` 래핑 → Transaction 상태 COMPLETED 업데이트

### 3-2. DID VP 프로토타입 스킵 모드 (기존 코드 수정)

| 파일 | 스킵 대상 |
|------|---------|
| `VpOfferApplicationService.java` | SDK 호출 스킵 → 더미 VpOfferPayload |
| `VpProfileApplicationService.java` | E2E + 서명 스킵 |
| `VpVerificationApplicationService.java` | AccE2e 검증 + VP 서명 검증 스킵 |
| `VpConfirmApplicationService.java` | 더미 ConfirmVerifyResDto |

### 산출물 확인

- [ ] OID4VP: `GET /oid4vp/request/{id}` → JWT Authorization Request 반환
- [ ] OID4VP: `POST /oid4vp/response {vp_token, state}` → 검증 처리 + Transaction COMPLETED
- [ ] DID VP: 기존 4단계 플로우 프로토타입 모드 동작

---

## Phase 4: 결과 조회 + Demo-Server 연동

> 프로토콜 무관하게 결과를 조회하는 통합 API와 Demo 화면.

### 4-1. Status API (신규)

| 파일 | 역할 |
|------|------|
| `protocol/orchestrator/StatusQueryService.java` | Transaction + VpSubmit/OID4VP 결과 조합 |
| `protocol/api/dto/StatusResponse.java` | `sessionId`, `protocol`, `status`, `result`, `error` |
| `UnifiedVerificationController.java` 추가 | `GET /v2/status/{sessionId}` |

### 4-2. Demo-Server 변경

> demo-server는 프론트엔드(React Admin Console)에 Demo 페이지를 추가하는 것이 아니라,
> 기존 demo-server 백엔드의 API 호출 방식을 `/v2/initiate`로 전환.

| 대상 | 변경 내용 |
|------|---------|
| `DemoServiceImpl.java` | `verifierFeign.requestVpOfferQR()` → `/v2/initiate` 호출로 전환 |
| `DemoServiceImpl.java` | 응답의 `protocol` 필드로 QR 데이터 분기 (DID VP: payload 기반, OID4VP: authorizationRequest 기반) |
| `VpResultDto.java` | `protocol` 필드 추가 |
| Demo Feign Client | `/v2/initiate`, `/v2/status/{sessionId}` 호출 메서드 추가 |
| Demo 프론트 | Policy 선택 UI에서 protocolType 표시, QR 생성 분기 |

### 4-3. React Admin Console — Demo 페이지 (신규, 선택적)

| 파일 | 역할 |
|------|------|
| `apis/verification-api.ts` | `/v2/initiate`, `/v2/status/{sessionId}`, Wallet 시뮬레이션 함수 |
| `pages/verification-demo/VerificationDemoPage.tsx` | Policy 선택 → QR → Wallet 시뮬레이션 → 결과 |
| `pages/verification-demo/QRCodeDisplay.tsx` | 프로토콜별 QR 렌더링 |
| `pages/verification-demo/VerificationResult.tsx` | 결과 표시 |
| `apis/models/VerificationDto.ts` | 타입 정의 |

### 산출물 확인

- [ ] `GET /v2/status/{sessionId}` → 현재 상태 반환
- [ ] Demo 화면에서 DID VP E2E 시나리오 동작
- [ ] Demo 화면에서 OID4VP E2E 시나리오 동작

---

## Phase 5: Admin API 확장 + OID4VP 설정 관리

> 프로토타입 이후. Admin Console에서 OID4VP Config, DCQL Scope Mapping, Policy를 관리.

### 5-1. Admin API (신규)

| 엔드포인트 | 역할 |
|-----------|------|
| `GET/PUT /admin/oid4vp/config` | OID4VP Config 조회/수정 |
| `GET/POST /admin/oid4vp/dcql-scope-mapping` | DCQL Scope Mapping CRUD |
| `GET/PUT/DELETE /admin/oid4vp/dcql-scope-mapping/{id}` | 개별 매핑 관리 |
| 기존 Policy CRUD 확장 | `protocolType`, `scope` 필드 추가 |

### 5-2. Admin UI 확장

| 화면 | 변경 내용 |
|------|---------|
| Policy 목록 | `protocolType` 배지 표시 (DID VP / OID4VP) |
| Policy 등록/수정 | protocolType 선택 시 폼 분기 (DID VP: 4단계, OID4VP: scope 선택) |
| OID4VP Config | `admin-oid4vp-mockup.html` 화면 ② 구현 |
| DCQL Scope Mapping | `admin-oid4vp-mockup.html` 화면 ③④ 구현 |

---

## Phase 6: 프로덕션 전환

> 프로토타입 스킵 모드를 제거하고 실제 서명 검증/암호화를 활성화.

| 항목 | 작업 |
|------|------|
| OID4VP SDK Repository | InMemory → JPA 구현체로 교체 (DB 테이블 기반) |
| JWT 서명 | 고정 키페어 → 실제 키 관리 (DID Document 기반) |
| VP Token 검증 | 더미 검증 → 실제 서명 검증 + DCQL 매칭 |
| DID VP E2E 암호화 | 스킵 → 실제 ECDH + AES-256-CBC |
| 블록체인/TAS 연동 | 더미 → 실제 호출 |
| 성능 | Policy 조회 캐싱 전략 적용 |

---

## 작업 순서 요약

```
Phase 0  DB 스키마 설계 확정
  │      (이 문서의 테이블 정의 리뷰 & 확정)
  ▼
Phase 1  기반 — SDK 통합 + Domain + Liquibase
  │      (빌드 성공 + 테이블 생성 확인)
  ▼
Phase 2  통합 진입점 — POST /v2/initiate
  │      (프로토콜 라우팅 동작 확인)
  ▼
Phase 3  후속 플로우 — /oid4vp/request, /oid4vp/response
  │      (프로토콜별 E2E 동작 확인)
  ▼
Phase 4  결과 조회 + Demo-Server 연동
  │      (GET /v2/status + Demo 화면 E2E 시나리오)
  ▼
Phase 5  Admin API + UI 확장
  │      (OID4VP Config, DCQL Mapping, Policy 관리)
  ▼
Phase 6  프로덕션 전환
         (스킵 모드 제거, 실제 서명/암호화)
```

### 서버 개발자 기준 우선순위

```
[즉시]  Phase 0 → 1 → 2    (DB + Domain + 라우팅)
[다음]  Phase 3 → 4          (후속 플로우 + Demo 연동)
[이후]  Phase 5 → 6          (Admin + 프로덕션)
```

---

## 전체 파일 수 요약

```
                        신규        수정        합계
Liquibase 마이그레이션     4개         1개         5개
Domain/Enum              5개         2개         7개
Protocol 레이어           6개         -           6개
Controller/DTO           5개         -           5개
Config                   2개         2개         4개
Demo-Server              2개         3개         5개
React (선택적)            5개         4개         9개
────────────────────────────────────────────────
합계                     29개        12개        41개
```

---

## 체크리스트

- [x] Phase 0: DB 스키마 리뷰 완료 (policy nullable 변경 영향도 확인)
- [x] Phase 1: `./gradlew bootJar -DskipFrontendBuild=true` 빌드 성공
- [x] Phase 1: H2에서 신규 테이블 생성 확인
- [x] Phase 2: `/v2/initiate` DID VP/OID4VP 분기 동작
- [x] Phase 3: OID4VP 후속 플로우 E2E 동작
- [ ] Phase 4: Demo 화면에서 양쪽 프로토콜 시나리오 완주 (Status API 구현됨, Demo-Server 연동 미완)
- [x] Phase 5: Admin에서 OID4VP Config/DCQL 관리 가능 (mockup 반영 완료)
- [ ] Phase 6: 프로토타입 스킵 모드 제거 + 실제 서명 검증

---

## 작업 진행 현황 (2026-03-24 기준)

### Phase 0~1: 완료

DB 스키마, Liquibase 마이그레이션, Domain 엔티티, SDK 통합은 이전에 모두 완료됨.

- Liquibase: `set.3/` 디렉토리에 4개 마이그레이션 파일 생성
- Domain: `ProtocolType` enum, `Oid4vpConfig`, `DcqlScopeMapping`, `Oid4vpSessionMapping` 엔티티
- `Policy` 엔티티에 `protocolType`, `scope` 필드 추가
- SDK 통합: `did-oid4vp-sdk-server` composite build 연동
- JPA Adapter: `JpaOid4vpRepositoryAdapter`, `JpaDcqlScopeMappingRepositoryAdapter` 구현
- `OID4VPIntegrationConfig` 빈 설정 (`@ConditionalOnMissingBean` 패턴으로 SDK InMemory 구현체 오버라이드)

### Phase 2~3: 프로토콜 라우팅 + OID4VP 엔드포인트 — 완료

Protocol 레이어 전체 구현 완료. 패키지: `verifier/v1/protocol/`

#### Protocol 핵심 구조

| 파일 | 역할 |
|------|------|
| `protocol/handler/ProtocolHandler.java` | 인터페이스: `getProtocolType()`, `initiate(InitiateRequest)` |
| `protocol/handler/DidVpProtocolHandler.java` | 기존 DID VP 플로우 래핑 |
| `protocol/handler/Oid4vpProtocolHandler.java` | OID4VP SDK `InitiationService` 래핑 + 세션 매핑 |
| `protocol/registry/ProtocolRegistry.java` | `Map<ProtocolType, ProtocolHandler>` 자동 디스커버리 |
| `protocol/resolver/PolicyProtocolResolver.java` | Policy → ProtocolType 결정 |
| `protocol/orchestrator/VerificationOrchestrator.java` | resolver → registry → handler 체인 |
| `protocol/orchestrator/StatusQueryService.java` | 프로토콜 무관 상태 조회 |

#### Controller / 엔드포인트

| 파일 | 엔드포인트 |
|------|-----------|
| `protocol/api/UnifiedVerificationController.java` | `POST /v2/initiate` — 통합 진입점 |
| | `GET /v2/status/{sessionId}` — 상태 조회 |
| `protocol/api/OID4VPController.java` | `GET /oid4vp/request/{requestId}` — Authorization Request JWT |
| | `POST /oid4vp/response` — VP Token 제출 |
| `protocol/service/OID4VPService.java` | Authorization Request 조회 및 Response 처리 |

### Phase 4: Status API + Demo-Server 연동 — 부분 완료

- **완료**: `StatusQueryService`, `GET /v2/status/{sessionId}` 엔드포인트
- **미완료**: Demo-Server 측 변경 (Feign 클라이언트 `/v2/initiate` 전환, Demo 프론트 QR 분기)

### Phase 5: Admin API + UI 확장 — 완료 (mockup 반영)

> Phase 5를 Phase 2~4보다 먼저 진행함 (Admin 설정이 프로토콜 동작의 선행 조건)

#### Backend (did-verifier-server)

**신규 생성:**

| 파일 | 역할 |
|------|------|
| `admin/controller/Oid4vpConfigController.java` | `GET/PUT /admin/oid4vp/config` — OID4VP Config JSON 조회/수정 |
| `admin/service/Oid4vpConfigService.java` | Config JSON 읽기/쓰기, SDK `VerifierConfigService.reloadConfig()` 호출 |
| `admin/controller/DcqlScopeMappingController.java` | 6개 엔드포인트: LIST, GET, POST, PUT, DELETE, POPUP |
| `admin/service/DcqlScopeMappingService.java` | DCQL Scope Mapping CRUD, SDK `ScopeToDCQLMapperService.reloadMappings()` 호출 |
| `admin/dto/DcqlScopeMappingDTO.java` | Scope Mapping DTO (`toDTO` 팩토리 메서드 포함) |

**수정:**

| 파일 | 변경 내용 |
|------|---------|
| `base/constants/UrlConstant.java` | OID4VP Admin URL 상수 8개 추가 |
| `admin/dto/PolicyDTO.java` | `protocolType`, `scope` 필드 추가, `toDTO()` 메서드 업데이트 |
| `admin/service/PolicyService.java` | `savePolicy()`에 protocolType/scope 설정, protocolType 기반 검색 오버로드 추가 |
| `admin/service/PolicyQueryService.java` | protocolType 필터 지원하는 검색 메서드 추가 |
| `admin/controller/PolicyController.java` | `protocolType` 쿼리 파라미터(optional) 추가 |
| `base/db/repository/PolicyRepositoryAdmin.java` | protocolType 파라미터 받는 인터페이스 메서드 추가 |
| `base/db/repository/PolicyRepositoryAdminImpl.java` | QueryDSL에 protocolType 필터 조건 추가 |

#### Frontend (did-verifier-admin/frontend)

**신규 생성:**

| 파일 | 역할 |
|------|------|
| `apis/oid4vp-api.ts` | OID4VP Config / DCQL Scope Mapping / OID4VP Policy / TAS Schema 조회 API |
| `pages/oid4vp-management/config/Oid4vpConfigPage.tsx` | 구조화된 폼 기반 OID4VP Config 관리 (scheme=`decentralized_identity` 기본, Client ID 편집가능, Encryption Key 표시, JSON Preview) |
| `pages/oid4vp-management/scope-mapping/ScopeMappingManagementPage.tsx` | Scope Mapping 목록 (DataGrid + 페이지네이션) |
| `pages/oid4vp-management/scope-mapping/ScopeMappingRegistrationPage.tsx` | DCQL 빌더 폼 — 단일 CredentialQuery, purpose 제거, claims 수동추가, meta JSON 배열, opendid_vc TAS 조회 |
| `pages/oid4vp-management/scope-mapping/ScopeMappingDetailPage.tsx` | Scope Mapping 상세 조회 |
| `pages/oid4vp-management/scope-mapping/ScopeMappingEditPage.tsx` | DCQL 빌더 폼 (Registration과 동일 구조 + 기존 데이터 로드) |
| `pages/oid4vp-management/policy/Oid4vpPolicyManagementPage.tsx` | OID4VP Policy 목록 (protocolType=OID4VP 필터) |
| `pages/oid4vp-management/policy/Oid4vpPolicyRegistrationPage.tsx` | OID4VP Policy 등록 (Profile + Payload + Scope 검색) |
| `pages/oid4vp-management/policy/Oid4vpPolicyDetailPage.tsx` | OID4VP Policy 상세 조회 |
| `pages/oid4vp-management/policy/Oid4vpPolicyEditPage.tsx` | OID4VP Policy 수정 |

**수정:**

| 파일 | 변경 내용 |
|------|---------|
| `config/navigationConfig.tsx` | OID4VP Management 메뉴 그룹 추가 (Config, Scope Mapping, Policy) |
| `main.tsx` | OID4VP 라우트 9개 추가 |

#### Mockup 반영 내역 (2026-03-24, `admin-oid4vp-mockup.html` 기반 팀 논의 결과)

**OID4VP Config:**
- Client ID Scheme 기본값 `decentralized_identity` (not `did`)
- Client ID Value: 자동 생성되지만 편집 가능 (프록시 환경 대응)
- VP Token Encryption Key: application.yml에서 관리, 화면에서 읽기전용 표시

**DCQL Scope Mapping 등록/수정:**
- `purpose` 필드 전체 제거 (CredentialQuery, ClaimQuery 모두 — 비표준 + dead code)
- 단일 CredentialQuery만 허용 (추가/삭제 버튼 제거, Claim Sets 미지원)
- Claims 기본 비어있음, "+ Add Claim" 버튼으로 수동 추가
- Meta Values: JSON 배열 형식 (`["value1", "value2"]`)
- `opendid_vc` format: Claims 섹션 숨김 (전체 credential 제출), TAS 조회 팝업으로 Credential Schema ID 선택
- Format 변경 시 claims 자동 초기화
- 빈 credentials 허용 (최소 1개 제한 제거)

#### 빌드 상태

- Backend: `./gradlew compileJava` — BUILD SUCCESSFUL
- Frontend: `npx tsc --noEmit` — 에러 없음
- Frontend: `npm run build` — 빌드 성공

### 다음 작업

| 우선순위 | Phase | 내용 | 상태 |
|---------|-------|------|------|
| 1 | Phase 4 잔여 | Demo-Server `/v2/initiate` 전환, Demo 프론트 QR 분기 | 미착수 |
| 2 | Phase 6 | 프로토타입 스킵 모드 제거, 실제 서명/암호화 활성화 | 미착수 |
| 3 | Phase 5 보완 | TAS Credential Schema 조회 백엔드 API 구현 (`/admin/oid4vp/tas/credential-schemas`) | 미착수 |
