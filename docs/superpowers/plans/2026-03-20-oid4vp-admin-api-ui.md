# OID4VP Admin API + UI Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** OID4VP Config, DCQL Scope Mapping, OID4VP Policy의 Admin CRUD API와 React UI 페이지를 구현한다.

**Architecture:** 기존 admin CRUD 패턴(Controller → Service → JPA Repository)을 그대로 따름. SDK의 JPA Adapter와 Entity/Repository는 이미 구현 완료. PolicyDTO/Service를 OID4VP용으로 확장하고, 프론트엔드는 기존 MUI+Toolpad Core 패턴으로 페이지 생성.

**Tech Stack:** Java 21, Spring Boot 3.2.4, JPA, React 19, TypeScript 5, MUI v6, Toolpad Core

---

## File Map

### Backend (source/did-verifier-server/src/main/java/org/omnione/did/)

| Action | File | Purpose |
|--------|------|---------|
| Modify | `base/constants/UrlConstant.java` | OID4VP admin URL 상수 추가 |
| Create | `verifier/v1/admin/controller/Oid4vpConfigController.java` | Config GET/PUT API |
| Create | `verifier/v1/admin/service/Oid4vpConfigService.java` | Config 조회/저장 서비스 |
| Create | `verifier/v1/admin/controller/DcqlScopeMappingController.java` | Scope Mapping CRUD API |
| Create | `verifier/v1/admin/service/DcqlScopeMappingService.java` | Scope Mapping CRUD 서비스 |
| Create | `verifier/v1/admin/dto/DcqlScopeMappingDTO.java` | Scope Mapping DTO |
| Modify | `verifier/v1/admin/dto/PolicyDTO.java` | scope, protocolType 필드 추가 |
| Modify | `verifier/v1/admin/service/PolicyService.java` | OID4VP Policy 저장 로직 추가 |

### Frontend (source/did-verifier-admin/frontend/src/)

| Action | File | Purpose |
|--------|------|---------|
| Modify | `config/navigationConfig.tsx` | OID4VP Management 메뉴 그룹 추가 |
| Modify | `main.tsx` | OID4VP 라우트 추가 |
| Create | `apis/oid4vp-api.ts` | OID4VP API 함수 |
| Create | `pages/oid4vp-management/config/Oid4vpConfigPage.tsx` | Config 설정 페이지 |
| Create | `pages/oid4vp-management/scope-mapping/ScopeMappingManagementPage.tsx` | Scope Mapping 목록 |
| Create | `pages/oid4vp-management/scope-mapping/ScopeMappingRegistrationPage.tsx` | Scope Mapping 등록 |
| Create | `pages/oid4vp-management/scope-mapping/ScopeMappingDetailPage.tsx` | Scope Mapping 상세 |
| Create | `pages/oid4vp-management/scope-mapping/ScopeMappingEditPage.tsx` | Scope Mapping 수정 |
| Create | `pages/oid4vp-management/policy/Oid4vpPolicyManagementPage.tsx` | OID4VP Policy 목록 |
| Create | `pages/oid4vp-management/policy/Oid4vpPolicyRegistrationPage.tsx` | OID4VP Policy 등록 |

---

## Task 1: Backend — URL 상수 + OID4VP Config Admin API

**Files:**
- Modify: `base/constants/UrlConstant.java:109` (Verifier class 끝 부분)
- Create: `verifier/v1/admin/controller/Oid4vpConfigController.java`
- Create: `verifier/v1/admin/service/Oid4vpConfigService.java`

- [ ] **Step 1: UrlConstant에 OID4VP admin 상수 추가**

```java
// UrlConstant.Verifier 클래스 내부, ZKP Profile 아래에 추가
// OID4VP Config
public static final String GET_OID4VP_CONFIG = "/oid4vp/config";
public static final String UPDATE_OID4VP_CONFIG = "/oid4vp/config";

// DCQL Scope Mapping
public static final String GET_SCOPE_MAPPING_LIST = "/oid4vp/scope-mappings";
public static final String SAVE_SCOPE_MAPPING = "/oid4vp/scope-mappings";
public static final String GET_SCOPE_MAPPING_INFO = "/oid4vp/scope-mappings/{id}";
public static final String UPDATE_SCOPE_MAPPING = "/oid4vp/scope-mappings/{id}";
public static final String DELETE_SCOPE_MAPPING = "/oid4vp/scope-mappings/{id}";
public static final String GET_POPUP_SCOPE_MAPPING_LIST = "/oid4vp/scope-mappings/popups/{searchValue}";
```

- [ ] **Step 2: Oid4vpConfigService 생성**

SDK의 `VerifierConfigService`와 서버의 `Oid4vpConfigRepository`를 조합. GET은 DB에서 config JSON 반환, PUT은 저장 후 SDK 캐시 갱신.

- [ ] **Step 3: Oid4vpConfigController 생성**

`GET /verifier/admin/v1/oid4vp/config` → 현재 설정 JSON 반환
`PUT /verifier/admin/v1/oid4vp/config` → 설정 저장 + SDK 캐시 갱신

- [ ] **Step 4: 컴파일 확인**

```bash
cd source/did-verifier-server && ./gradlew compileJava -DskipFrontendBuild=true
```

---

## Task 2: Backend — DCQL Scope Mapping Admin API

**Files:**
- Create: `verifier/v1/admin/dto/DcqlScopeMappingDTO.java`
- Create: `verifier/v1/admin/service/DcqlScopeMappingService.java`
- Create: `verifier/v1/admin/controller/DcqlScopeMappingController.java`

- [ ] **Step 1: DcqlScopeMappingDTO 생성**

필드: id, scope, dcqlQuery(String), description, enabled, createdAt(String)
`toDTO(DcqlScopeMapping entity)` 정적 팩토리 메서드 포함

- [ ] **Step 2: DcqlScopeMappingService 생성**

JPA `DcqlScopeMappingRepository` 사용. CRUD + 검색 + popup 리스트.
save 시 SDK `ScopeToDCQLMapperService.reloadMappings()` 호출하여 캐시 동기화.

- [ ] **Step 3: DcqlScopeMappingController 생성**

기존 FilterController 패턴 따름. 6개 엔드포인트 (LIST, GET, POST, PUT, DELETE, POPUP).

- [ ] **Step 4: 컴파일 확인**

---

## Task 3: Backend — Policy API OID4VP 확장

**Files:**
- Modify: `verifier/v1/admin/dto/PolicyDTO.java`
- Modify: `verifier/v1/admin/service/PolicyService.java`

- [ ] **Step 1: PolicyDTO에 OID4VP 필드 추가**

`scope` (String), `protocolType` (ProtocolType) 필드 추가. `toDTO()` 팩토리에도 반영.

- [ ] **Step 2: PolicyService.savePolicy() OID4VP 분기 추가**

`protocolType == OID4VP`일 때: `scope` 설정, `payloadId`/`policyProfileId`는 null 허용.

- [ ] **Step 3: 컴파일 확인**

---

## Task 4: Frontend — Navigation + Routes + API

**Files:**
- Modify: `config/navigationConfig.tsx`
- Modify: `main.tsx`
- Create: `apis/oid4vp-api.ts`

- [ ] **Step 1: navigationConfig에 OID4VP Management 그룹 추가**

VP Policy Management 아래, ZKP 위에 배치. children: OID4VP Config, DCQL Scope Mapping, OID4VP Policy

- [ ] **Step 2: main.tsx에 OID4VP 라우트 추가**

```
/oid4vp-management/config
/oid4vp-management/scope-mapping (+ /:id, /scope-mapping-registration, /scope-mapping-edit/:id)
/oid4vp-management/policy (+ /oid4vp-policy-registration)
```

- [ ] **Step 3: oid4vp-api.ts 생성**

Config: `getOid4vpConfig()`, `putOid4vpConfig(data)`
Scope Mapping: `fetchScopeMappings()`, `getScopeMapping(id)`, `postScopeMapping(data)`, `putScopeMapping(data)`, `deleteScopeMapping(id)`, `searchScopeMappingList(searchValue)`
Policy: 기존 vp-policy-api 재사용 (policyType 파라미터 활용)

---

## Task 5: Frontend — OID4VP Config 페이지

**Files:**
- Create: `pages/oid4vp-management/config/Oid4vpConfigPage.tsx`

- [ ] **Step 1: Config 페이지 구현**

단일 폼 페이지. useEffect로 GET → 폼에 채움 → Save 시 PUT.
필드: baseUrl, clientName, clientId.scheme (Select), clientId.value (scheme별 자동/DID조회), sessionTtl, invocationScheme.
고정값(endpoints, clientMetadata, crypto)은 저장 시 JSON에 자동 포함.
JSON 미리보기 영역 포함.

---

## Task 6: Frontend — DCQL Scope Mapping 페이지 (CRUD 4개)

**Files:**
- Create: `pages/oid4vp-management/scope-mapping/ScopeMappingManagementPage.tsx`
- Create: `pages/oid4vp-management/scope-mapping/ScopeMappingRegistrationPage.tsx`
- Create: `pages/oid4vp-management/scope-mapping/ScopeMappingDetailPage.tsx`
- Create: `pages/oid4vp-management/scope-mapping/ScopeMappingEditPage.tsx`

- [ ] **Step 1: ManagementPage — 목록 페이지**

CustomDataGrid 사용. 컬럼: ID, Scope, Description, Credential수, Enabled, 수정일.
서버 페이징. Register/Edit/Delete 버튼.

- [ ] **Step 2: RegistrationPage — 등록 페이지**

기본정보(scope, description, enabled) + DCQLQuery 빌더.
CredentialQuery 동적 추가/삭제. Format 선택(dc+sd-jwt, vc+sd-jwt, opendid_vc).
Format별 meta key 자동 전환. Claims 동적 추가/삭제.
저장 시 JSON 조립하여 POST.

- [ ] **Step 3: DetailPage — 상세 페이지**

읽기 전용. DCQLQuery JSON 표시.

- [ ] **Step 4: EditPage — 수정 페이지**

RegistrationPage와 동일 폼, 기존 데이터 로드. 변경 감지 후 Save 활성화.

---

## Task 7: Frontend — OID4VP Policy 페이지

**Files:**
- Create: `pages/oid4vp-management/policy/Oid4vpPolicyManagementPage.tsx`
- Create: `pages/oid4vp-management/policy/Oid4vpPolicyRegistrationPage.tsx`

- [ ] **Step 1: ManagementPage — Policy 목록**

기존 PolicyManagementPage 패턴. `policyType=VP&protocolType=OID4VP` 파라미터로 필터.
컬럼: ID, Policy Title, Scope, 등록일.

- [ ] **Step 2: RegistrationPage — Policy 등록**

policyTitle + DCQL Scope Mapping 검색 팝업(SearchDialog).
scope 선택 → 저장 시 protocolType=OID4VP 세팅.

---

## Task 8: 통합 빌드 확인

- [ ] **Step 1: 백엔드 빌드**
```bash
cd source/did-verifier-server && ./gradlew compileJava -DskipFrontendBuild=true
```

- [ ] **Step 2: 프론트엔드 빌드**
```bash
cd source/did-verifier-admin/frontend && npm run build
```
