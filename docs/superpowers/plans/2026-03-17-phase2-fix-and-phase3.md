# Phase 2 수정 + Phase 3 OID4VP 후속 플로우 구현

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SDK Composite Build 통합, BouncyCastle 1.80 업그레이드, Oid4vpProtocolHandler SDK 연동, Phase 3 OID4VP 엔드포인트 구현

**Architecture:** OID4VP SDK를 Composite Build로 통합하고, Oid4vpProtocolHandler가 SDK의 InitiationService를 직접 호출하도록 수정. Phase 3에서는 OID4VPController를 추가해 `/oid4vp/request/{requestId}`, `/oid4vp/response` 엔드포인트를 구현. SDK의 InMemory Repository를 프로토타입에서 그대로 사용하되, SDK Repository에 JPA 데이터를 동기화하는 Adapter 패턴 적용.

**Tech Stack:** Java 21, Spring Boot 3.2.4, OID4VP SDK (Composite Build), BouncyCastle 1.80, nimbus-jose-jwt 9.37.4

---

## 파일 구조

### 수정 대상
| 파일 | 변경 내용 |
|------|---------|
| `settings.gradle` | `includeBuild('did-oid4vp-sdk-server')` 추가 |
| `build.gradle` | SDK 의존성 추가, BouncyCastle 1.78.1→1.80, nimbus-jose-jwt 추가 |
| `did-oid4vp-sdk-server/build.gradle` | fat JAR → thin JAR 전환 |
| `Oid4vpProtocolHandler.java` | SDK InitiationService 호출로 전면 교체 |
| (ComponentScan 수정 불필요) | `@SpringBootApplication`이 `org.omnione.did` 패키지 → SDK `org.omnione.did.oid4vc.oid4vp` 자동 포함 |
| `UrlConstant.java` | OID4VP 엔드포인트 상수 추가 |
| `ErrorCode.java` | Phase 3 에러코드 추가 |

### 신규 생성
| 파일 | 역할 |
|------|------|
| `protocol/config/OID4VPIntegrationConfig.java` | OID4VP SDK 초기 데이터 로딩 + KeyPair Bean |
| `protocol/api/OID4VPController.java` | GET /oid4vp/request/{requestId}, POST /oid4vp/response |

---

## Task 1: SDK Composite Build 통합 + BouncyCastle 업그레이드

**Files:**
- Modify: `source/did-verifier-server/settings.gradle`
- Modify: `source/did-verifier-server/build.gradle`
- Modify: `source/did-verifier-server/did-oid4vp-sdk-server/build.gradle`

- [ ] **Step 1: did-oid4vp-sdk-server를 thin JAR로 전환**

`did-oid4vp-sdk-server/build.gradle`에서 fat JAR 설정을 제거하고 thin JAR로 변경:

```gradle
// 변경 전 (fat JAR):
jar {
    enabled = true
    from {
        configurations.runtimeClasspath.findAll { it.name.endsWith('jar') }.collect { zipTree(it) }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'META-INF/LICENSE*', 'META-INF/NOTICE*'
    exclude 'org/springframework/**'
    exclude 'org/apache/**'
    exclude 'org/slf4j/**'
    exclude 'ch/qos/logback/**'
    archiveClassifier.set('')
}

// 변경 후 (thin JAR):
jar {
    enabled = true
}
```

- [ ] **Step 2: settings.gradle에 Composite Build 추가**

```gradle
// 추가:
includeBuild('did-oid4vp-sdk-server')
```

- [ ] **Step 3: build.gradle에 의존성 추가 + BouncyCastle 업그레이드**

```gradle
// 변경: BouncyCastle 1.78.1 → 1.80
implementation 'org.bouncycastle:bcpkix-jdk18on:1.80'
implementation 'org.bouncycastle:bcprov-jdk18on:1.80'
implementation 'org.bouncycastle:bcutil-jdk18on:1.80'

// 추가: OID4VP SDK
implementation 'org.omnione.did:did-oid4vp-sdk-server'

// 추가: JWT (SDK 런타임 의존성)
implementation 'com.nimbusds:nimbus-jose-jwt:9.37.4'
```

- [ ] **Step 4: 빌드 확인**

Run: `cd source/did-verifier-server && ./gradlew clean compileJava -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add settings.gradle build.gradle did-oid4vp-sdk-server/build.gradle
git commit -m "build: integrate OID4VP SDK as composite build and upgrade BouncyCastle to 1.80"
```

---

## Task 2: SDK VerifierConfigService 기동 안전성 수정

**Files:**
- Modify: `source/did-verifier-server/did-oid4vp-sdk-server/src/main/java/org/omnione/did/oid4vc/oid4vp/service/VerifierConfigService.java`

SDK의 `VerifierConfigService.init()`는 `@PostConstruct`에서 `loadConfig()`를 호출하는데, InMemory Repository가 비어있으면 `RuntimeException`을 던져서 앱 기동이 실패한다. JPA 데이터를 SDK Repository에 동기화하는 시점(`ApplicationReadyEvent`)보다 `@PostConstruct`가 먼저 실행되므로, 반드시 graceful하게 처리해야 한다.

- [ ] **Step 1: VerifierConfigService.init() 수정**

```java
// 변경 전:
@PostConstruct
public void init() {
    loadConfig();
}

// 변경 후:
@PostConstruct
public void init() {
    try {
        loadConfig();
    } catch (RuntimeException e) {
        log.warn("OID4VP config not found at startup. Will be loaded after data sync. {}", e.getMessage());
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add did-oid4vp-sdk-server/src/main/java/org/omnione/did/oid4vc/oid4vp/service/VerifierConfigService.java
git commit -m "fix: make VerifierConfigService startup graceful when config is missing"
```

---

## Task 3: OID4VP 초기 설정 Config + ComponentScan 확인

**Files:**
- Create: `source/did-verifier-server/src/main/java/org/omnione/did/verifier/v1/protocol/config/OID4VPIntegrationConfig.java`

SDK의 `VerifierConfigService`가 `@PostConstruct`에서 `OID4VPRepository.findByType("OID4VP")`를 호출하므로, 서버 기동 시 SDK의 InMemory Repository에 초기 데이터가 있어야 한다.

- [ ] **Step 1: OID4VPIntegrationConfig 작성**

```java
package org.omnione.did.verifier.v1.protocol.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.DcqlScopeMapping;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.oid4vc.oid4vp.dto.DCQLScopeMappingDto;
import org.omnione.did.oid4vc.oid4vp.dto.OID4VPConfigDto;
import org.omnione.did.oid4vc.oid4vp.repository.OID4VPRepository;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OID4VPIntegrationConfig {

    private final Oid4vpConfigRepository jpaConfigRepo;
    private final org.omnione.did.base.db.repository.DcqlScopeMappingRepository jpaDcqlRepo;
    private final OID4VPRepository sdkConfigRepo;
    private final org.omnione.did.oid4vc.oid4vp.repository.DCQLScopeMappingRepository sdkDcqlRepo;
    private final VerifierConfigService verifierConfigService;

    @Getter
    private KeyPair verifierKeyPair;

    @EventListener(ApplicationReadyEvent.class)
    public void syncConfigToSdk() {
        syncOid4vpConfig();
        syncDcqlMappings();
        generatePrototypeKeyPair();
        verifierConfigService.reloadConfig();
        log.info("OID4VP SDK configuration synced from JPA");
    }

    private void syncOid4vpConfig() {
        jpaConfigRepo.findByType("OID4VP").ifPresent(config -> {
            sdkConfigRepo.save(OID4VPConfigDto.builder()
                    .type(config.getType())
                    .config(config.getConfig())
                    .build());
            log.info("Synced OID4VP config to SDK repository");
        });
    }

    private void syncDcqlMappings() {
        List<DcqlScopeMapping> mappings = jpaDcqlRepo.findAllByEnabledTrue();
        for (DcqlScopeMapping mapping : mappings) {
            sdkDcqlRepo.save(DCQLScopeMappingDto.builder()
                    .scope(mapping.getScope())
                    .dcqlQuery(mapping.getDcqlQuery())
                    .description(mapping.getDescription())
                    .enabled(mapping.getEnabled())
                    .build());
        }
        log.info("Synced {} DCQL scope mappings to SDK repository", mappings.size());
    }

    private void generatePrototypeKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            this.verifierKeyPair = kpg.generateKeyPair();
            log.info("Generated prototype EC key pair for OID4VP signing");
        } catch (Exception e) {
            log.error("Failed to generate key pair", e);
            throw new RuntimeException(e);
        }
    }
}
```

**참고:** JPA `DcqlScopeMappingRepository`와 SDK `DCQLScopeMappingRepository`가 이름 충돌하므로, JPA 쪽을 FQCN으로 사용. `@Getter`로 `verifierKeyPair` 접근자 자동 생성.

**중요:** SDK의 `VerifierConfigService`가 `@PostConstruct`에서 config를 로드하려고 하는데, 이 시점에 아직 SDK Repository에 데이터가 없어서 실패할 수 있다. 이를 방지하기 위해 `@EventListener(ApplicationReadyEvent.class)`를 사용하여 Liquibase 마이그레이션 이후에 동기화한다. 단, `VerifierConfigService.init()` 실패를 방지하려면 SDK 쪽 `@PostConstruct`를 try-catch로 감싸거나, 초기 데이터가 먼저 들어가야 한다. Task 5의 Liquibase 초기 데이터가 선행 조건.

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/omnione/did/verifier/v1/protocol/config/OID4VPIntegrationConfig.java
git commit -m "feat: add OID4VP SDK integration config with JPA-to-SDK sync"
```

---

## Task 4: Oid4vpProtocolHandler SDK 연동으로 교체

**Files:**
- Modify: `source/did-verifier-server/src/main/java/org/omnione/did/verifier/v1/protocol/handler/Oid4vpProtocolHandler.java`

현재 프로토타입 코드를 SDK `InitiationService.initiateVerification()` 호출로 전면 교체.

- [ ] **Step 1: Oid4vpProtocolHandler 전면 재작성**

```java
package org.omnione.did.verifier.v1.protocol.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.DcqlScopeMapping;
import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.repository.DcqlScopeMappingRepository;
import org.omnione.did.base.db.repository.Oid4vpSessionMappingRepository;
import org.omnione.did.base.db.repository.PolicyRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.service.InitiationService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class Oid4vpProtocolHandler implements ProtocolHandler {

    private final PolicyRepository policyRepository;
    private final DcqlScopeMappingRepository dcqlScopeMappingRepository;
    private final TransactionService transactionService;
    private final Oid4vpSessionMappingRepository oid4vpSessionMappingRepository;
    private final InitiationService initiationService;

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OID4VP;
    }

    @Override
    @Transactional
    public InitiateResponse initiate(InitiateRequest request) {
        log.debug("=== OID4VP initiate for policyId: {} ===", request.getPolicyId());

        try {
            // 1. Policy 조회
            Policy policy = policyRepository.findByPolicyId(request.getPolicyId())
                    .orElseThrow(() -> new OpenDidException(ErrorCode.VP_POLICY_NOT_FOUND));

            // 2. Scope 검증
            String scope = policy.getScope();
            if (scope == null || scope.isBlank()) {
                throw new OpenDidException(ErrorCode.DCQL_SCOPE_MAPPING_NOT_FOUND);
            }

            // 3. SDK InitiationService 호출
            ServiceResult<Map<String, Object>> initResult =
                    initiationService.initiateVerification(
                            null,           // dcqlQuery (scope 사용)
                            scope,          // scope → SDK가 DCQL 변환
                            "direct_post",  // responseMode
                            null,           // clientMetadata
                            true            // useRequestUri (by_reference)
                    );

            if (!initResult.isSuccess()) {
                log.error("SDK initiation failed: {} - {}",
                        initResult.getErrorCode(), initResult.getErrorDescription());
                throw new OpenDidException(ErrorCode.OID4VP_INITIATION_FAILED);
            }

            Map<String, Object> sdkResponse = initResult.getData();

            // 4. SDK 응답에서 세션 정보 추출
            String oid4vpTransactionId = (String) sdkResponse.get("transaction_id");
            String requestId = (String) sdkResponse.get("request_id");
            String state = (String) sdkResponse.get("state");
            String authorizationRequestUri = (String) sdkResponse.get("authorization_request_uri");

            // 5. 통합 Transaction 생성
            Transaction transaction = transactionService.insertTransaction(Transaction.builder()
                    .txId(UUID.randomUUID().toString())
                    .type(TransactionType.OID4VP)
                    .status(TransactionStatus.PENDING)
                    .expired_at(transactionService.retrieveTransactionExpiredTime())
                    .build());

            // 6. 세션 매핑 저장 (통합 txId ↔ SDK 세션 ID)
            oid4vpSessionMappingRepository.save(Oid4vpSessionMapping.builder()
                    .txId(transaction.getTxId())
                    .oid4vpTransactionId(oid4vpTransactionId)
                    .oid4vpRequestId(requestId)
                    .state(state)
                    .build());

            log.debug("*** OID4VP initiate completed. txId={}, requestId={} ***",
                    transaction.getTxId(), requestId);

            // 7. 응답 구성
            return InitiateResponse.builder()
                    .protocol(ProtocolType.OID4VP)
                    .sessionId(transaction.getTxId())
                    .authorizationRequest(authorizationRequestUri)
                    .nextEndpoints(Map.of(
                            "authorizationRequest", "/oid4vp/request/" + requestId,
                            "response", "/oid4vp/response"
                    ))
                    .build();

        } catch (OpenDidException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initiate OID4VP verification", e);
            throw new OpenDidException(ErrorCode.OID4VP_INITIATION_FAILED);
        }
    }
}
```

핵심 변경점:
- `UUID.randomUUID()` 직접 생성 → `InitiationService.initiateVerification()` 호출
- SDK가 session을 `SessionRepository`에 저장하므로 Phase 3 endpoint에서 조회 가능
- `@Transactional` 추가 (Transaction + SessionMapping 원자성 보장)

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/omnione/did/verifier/v1/protocol/handler/Oid4vpProtocolHandler.java
git commit -m "feat: connect Oid4vpProtocolHandler to SDK InitiationService"
```

---

## Task 5: OID4VP Phase 3 엔드포인트 — Controller + ErrorCode

**Files:**
- Create: `source/did-verifier-server/src/main/java/org/omnione/did/verifier/v1/protocol/api/OID4VPController.java`
- Modify: `source/did-verifier-server/src/main/java/org/omnione/did/base/constants/UrlConstant.java`
- Modify: `source/did-verifier-server/src/main/java/org/omnione/did/base/exception/ErrorCode.java`

- [ ] **Step 1: UrlConstant에 OID4VP 경로 상수 추가**

```java
// UrlConstant.Verifier 클래스 내부에 추가:
public static final String OID4VP_REQUEST = "/oid4vp/request";
public static final String OID4VP_RESPONSE = "/oid4vp/response";
```

- [ ] **Step 2: ErrorCode에 Phase 3 에러코드 추가**

```java
// OID4VP Phase 3 에러코드 추가:
OID4VP_SESSION_NOT_FOUND("SSRVVRF01306", "OID4VP session not found.", 404),
OID4VP_AUTHORIZATION_REQUEST_FAILED("SSRVVRF01307", "Failed to retrieve authorization request.", 500),
OID4VP_RESPONSE_PROCESSING_FAILED("SSRVVRF01308", "Failed to process OID4VP response.", 500),
```

- [ ] **Step 3: OID4VPController 작성**

```java
package org.omnione.did.verifier.v1.protocol.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.omnione.did.base.db.repository.Oid4vpSessionMappingRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.service.AuthorizationService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.protocol.config.OID4VPIntegrationConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "OID4VP", description = "OID4VP protocol endpoints")
public class OID4VPController {

    private final AuthorizationService authorizationService;
    private final OID4VPIntegrationConfig oid4vpIntegrationConfig;
    private final Oid4vpSessionMappingRepository oid4vpSessionMappingRepository;
    private final TransactionService transactionService;

    // 프로토타입용 고정값 (Phase 6에서 DID Document 기반으로 교체)
    private static final String VERIFICATION_METHOD = "did:omn:verifier#key-1";
    private static final String PUBLIC_KEY_MULTIBASE = null; // 프로토타입에서는 null 허용

    @Operation(summary = "Get Authorization Request (JAR)",
            description = "Wallet calls this to retrieve the signed JWT Authorization Request")
    @GetMapping("/oid4vp/request/{requestId}")
    public ResponseEntity<String> getAuthorizationRequest(
            @PathVariable String requestId) {

        log.debug("=== GET /oid4vp/request/{} ===", requestId);

        KeyPair keyPair = oid4vpIntegrationConfig.getVerifierKeyPair();

        ServiceResult<String> result = authorizationService.getAuthorizationRequest(
                requestId,
                keyPair.getPrivate(),
                VERIFICATION_METHOD,
                PUBLIC_KEY_MULTIBASE
        );

        if (!result.isSuccess()) {
            log.error("Authorization request failed: {} - {}",
                    result.getErrorCode(), result.getErrorDescription());
            return ResponseEntity.status(result.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"" + result.getErrorCode()
                            + "\",\"error_description\":\"" + result.getErrorDescription() + "\"}");
        }

        String contentType = result.getContentType() != null
                ? result.getContentType()
                : "application/oauth-authz-req+jwt";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(result.getData());
    }

    @Operation(summary = "Receive VP Token Response",
            description = "Wallet submits VP Token after user authorization")
    @PostMapping("/oid4vp/response")
    public ResponseEntity<Map<String, Object>> receiveResponse(
            @RequestParam(value = "vp_token", required = false) String vpToken,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletRequest httpRequest) {

        log.debug("=== POST /oid4vp/response state={} ===", state);

        try {
            // VP Token 파싱 (SDK의 OID4VPHelperService 사용)
            Map<String, List<Object>> vpTokenMap = null;
            if (vpToken != null && !vpToken.isBlank()) {
                // 프로토타입: vpToken은 JSON Map 형태로 전달된다고 가정
                var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                vpTokenMap = objectMapper.readValue(vpToken,
                        objectMapper.getTypeFactory().constructMapType(
                                java.util.LinkedHashMap.class, String.class, List.class));
            }

            // SDK AuthorizationService에 위임
            // 프로토타입: issuerPublicKeys, holderPublicKeys는 null (서명 검증 스킵)
            ServiceResult<Map<String, Object>> result = authorizationService.receiveResponse(
                    vpTokenMap,
                    null,   // issuerPublicKeys (프로토타입 스킵)
                    null,   // holderPublicKeys (프로토타입 스킵)
                    state,
                    error,
                    errorDescription,
                    httpRequest.getMethod()
            );

            // Transaction 상태 업데이트
            if (state != null) {
                oid4vpSessionMappingRepository.findByState(state).ifPresent(mapping -> {
                    TransactionStatus newStatus = result.isSuccess()
                            ? TransactionStatus.COMPLETED
                            : TransactionStatus.FAILED;
                    transactionService.updateErrorTransactionStatus(
                            mapping.getTxId(), newStatus);
                    log.debug("Transaction {} status updated to {}",
                            mapping.getTxId(), newStatus);
                });
            }

            if (!result.isSuccess()) {
                return ResponseEntity.status(result.getHttpStatus())
                        .body(Map.of(
                                "error", result.getErrorCode(),
                                "error_description", result.getErrorDescription()
                        ));
            }

            return ResponseEntity.ok(result.getData());

        } catch (Exception e) {
            log.error("Failed to process OID4VP response", e);
            throw new OpenDidException(ErrorCode.OID4VP_RESPONSE_PROCESSING_FAILED);
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew compileJava -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/omnione/did/verifier/v1/protocol/api/OID4VPController.java \
       src/main/java/org/omnione/did/base/constants/UrlConstant.java \
       src/main/java/org/omnione/did/base/exception/ErrorCode.java
git commit -m "feat: add OID4VP controller for authorization request and response endpoints"
```

---

## Task 6: Liquibase 초기 데이터 + 테스트 빌드

**Files:**
- Create: `source/did-verifier-server/src/main/resources/db/changelog/changeset/set.3/protocol-init-data.xml`
- Modify: `source/did-verifier-server/src/main/resources/db/changelog/changeset/set_master.xml` (set.3 초기 데이터 추가)

SDK의 `VerifierConfigService`가 기동 시 OID4VP Config를 필요로 하므로, Liquibase에서 초기 데이터를 삽입해야 함.

- [ ] **Step 1: 초기 데이터 마이그레이션 작성**

`protocol-init-data.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="protocol-init-oid4vp-config" author="verifier">
        <preConditions onFail="MARK_RAN">
            <sqlCheck expectedResult="0">SELECT COUNT(*) FROM oid4vp_config WHERE type = 'OID4VP'</sqlCheck>
        </preConditions>
        <insert tableName="oid4vp_config">
            <column name="type" value="OID4VP"/>
            <column name="config" value='{"baseUrl":"http://localhost:8092","clientName":"OpenDID Verifier","invocationScheme":"openid4vp://","clientId":{"scheme":"redirect_uri","value":"http://localhost:8092/oid4vp/response"},"session":{"sessionTtl":300000},"endpoints":{"response":"/oid4vp/response","request":"/oid4vp/request"},"clientMetadata":{"vpFormatsSupported":{"jwt_vp_json":{"alg_values_supported":["ES256"]}}},"crypto":{"vpTokenEncryptionKey":null}}'/>
            <column name="created_at" valueComputed="NOW()"/>
        </insert>
    </changeSet>

    <changeSet id="protocol-init-dcql-scope-mapping" author="verifier">
        <preConditions onFail="MARK_RAN">
            <sqlCheck expectedResult="0">SELECT COUNT(*) FROM dcql_scope_mapping WHERE scope = 'id_card_verification'</sqlCheck>
        </preConditions>
        <insert tableName="dcql_scope_mapping">
            <column name="scope" value="id_card_verification"/>
            <column name="dcql_query" value='{"credentials":[{"id":"id_card","format":"jwt_vc_json","meta":{"vct":"NationalIdCredential"},"claims":[{"id":"name_claim","path":["name"]},{"id":"birth_claim","path":["birthDate"]}]}]}'/>
            <column name="description" value="National ID card verification - name and birth date"/>
            <column name="enabled" valueBoolean="true"/>
            <column name="created_at" valueComputed="NOW()"/>
        </insert>
    </changeSet>

    <changeSet id="protocol-init-oid4vp-policy" author="verifier">
        <preConditions onFail="MARK_RAN">
            <sqlCheck expectedResult="0">SELECT COUNT(*) FROM policy WHERE policy_id = 'policy-oid4vp-demo'</sqlCheck>
        </preConditions>
        <insert tableName="policy">
            <column name="policy_id" value="policy-oid4vp-demo"/>
            <column name="policy_title" value="OID4VP Demo Policy"/>
            <column name="policy_type" value="VP"/>
            <column name="protocol_type" value="OID4VP"/>
            <column name="scope" value="id_card_verification"/>
            <column name="created_at" valueComputed="NOW()"/>
        </insert>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: set_master.xml에 초기 데이터 include 추가**

set.3 관련 include가 이미 있는지 확인 후 `protocol-init-data.xml`을 추가.

- [ ] **Step 3: 전체 빌드 + 서버 기동 테스트**

Run: `./gradlew bootJar -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/changeset/set.3/protocol-init-data.xml \
       src/main/resources/db/changelog/changeset/set_master.xml
git commit -m "feat: add OID4VP prototype initial data via Liquibase"
```

---

## Task 7: 전체 통합 빌드 + 스모크 테스트

- [ ] **Step 1: Clean 빌드**

Run: `./gradlew clean bootJar -DskipFrontendBuild=true`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 컴파일 에러 없음 확인**

에러 발생 시:
- SDK 패키지 import 경로 확인 (`org.omnione.did.oid4vc.oid4vp.*`)
- BouncyCastle 버전 충돌 확인
- SDK libs/ 내 JAR 의존성 충돌 확인

- [ ] **Step 3: Commit (필요시)**

변경 사항이 있으면 커밋.

---

## 설계 결정 사항 (Medium 이슈 처리)

### #5 OID4VPConfig 이중 관리
→ **프로토타입에서는 분리 유지.** JPA에 저장된 config를 서버 기동 시 SDK InMemory Repository에 동기화하는 방식 (`OID4VPIntegrationConfig.syncConfigToSdk()`). Phase 6에서 JPA Repository adapter로 통합.

### #6 InitiateResponse DTO
→ **현행 유지.** `Object payload`는 DID VP 쪽 VpOfferPayload가 다양한 형태이므로 Object가 적절. `@JsonInclude(NON_NULL)`이 이미 적용되어 불필요한 필드는 응답에서 제외됨.

### #7 @Transactional
→ **Oid4vpProtocolHandler.initiate()에 `@Transactional` 추가.** Task 4에서 반영.
