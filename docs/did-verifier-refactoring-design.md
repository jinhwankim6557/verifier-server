# DID Verifier Server SDK화 - 실제 코드 기반 설계문서

## 1. 전체 아키텍처

### 1.1 변경 전 (AS-IS)

```mermaid
graph TD
    Client["🖥️ Client"]
    
    subgraph Current["DID Verifier Server (현재)"]
        Controller["VerifierController"]
        
        Service["VerifierServiceImpl<br/>(1000+ lines)<br/>- requestVpOfferbyQR<br/>- requestProfile<br/>- requestVerify<br/>- confirmVerify"]
        
        Repos["Repositories (직접 의존)<br/>- PolicyRepository<br/>- PayloadRepository<br/>- PolicyProfileRepository<br/>- VpFilterRepository<br/>- VpProcessRepository<br/>- VpOfferRepository<br/>- VpProfileRepository<br/>- VpSubmitRepository<br/>- E2eQueryService<br/>- ... 더 많음"]
        
        DB["🗄️ Database<br/>(PostgreSQL)"]
    end
    
    Client -->|HTTP| Controller
    Controller -->|직접 호출| Service
    Service -->|"직접 의존<br/>10+ Repository"| Repos
    Repos -->|CRUD| DB
    
    style Service fill:#ff6b6b,color:#fff
    style Repos fill:#ffe0e0
    style Current fill:#fff5f5
```

### 1.2 변경 후 (TO-BE)

```mermaid
graph TD
    Client["🖥️ Client"]
    
    subgraph VerifierApp["Verifier Application Server"]
        Controller["Controller<br/>(~100 lines)<br/>- HTTP 요청 수신<br/>- SDK 호출<br/>- Application Logic 처리<br/>- HTTP 응답 생성"]
        
        Services["Application Services<br/>✅ 명확한 책임 분리<br/>- TransactionServiceImpl<br/>- E2EQueryServiceImpl"]
        
        Providers["Interface 구현체<br/>(DTO 제공)<br/>- VerificationConfigProviderImpl<br/>  └─ Policy/Payload로부터<br/>     DTO 변환 제공<br/>- VerifierInfoProviderImpl<br/>- E2eSessionProviderImpl<br/>- StorageServiceImpl"]
        
        Repos["Repositories<br/>(Application만 사용)<br/>- PolicyRepository<br/>- PayloadRepository<br/>- VpFilterRepository<br/>- VpProcessRepository<br/>- VpOfferRepository<br/>- VpProfileRepository<br/>- VpSubmitRepository"]
        
        AppDB["🗄️ Application DB"]
    end
    
    subgraph SDK["did-verifier-sdk (JAR)<br/>✅ 프로토콜 로직만"]
        Interfaces["Integration Interfaces<br/>(7개)<br/>- VerificationConfigProvider<br/>- VerifierInfoProvider<br/>- E2eSessionProvider<br/>- StorageService<br/>- OfferTypeHandler<br/>- ... 등"]
        
        CoreService["Core Service<br/>(~300 lines)<br/>✅ 순수 함수만<br/>- requestVpOfferbyQR<br/>- requestProfile<br/>- requestVerify<br/>- confirmVerify"]
        
        Common["Common Layer<br/>- DTO들<br/>- Utils<br/>- Enum"]
    end
    
    Client -->|HTTP| Controller
    Controller -->|1. 데이터 준비<br/>Application Logic| Services
    Controller -->|2. SDK 호출<br/>Interface 통해| CoreService
    CoreService -->|의존<br/>Interface| Interfaces
    Services -->|구현| Interfaces
    Providers -->|DTO 반환| Interfaces
    Repos -->|CRUD| AppDB
    
    style Controller fill:#81C784,color:#fff
    style CoreService fill:#4CAF50,color:#fff
    style Interfaces fill:#e8f5e9
    style VerifierApp fill:#f0fff0
    style SDK fill:#f0f8ff
```

---

## 2. Interface 정의 (SDK - Integration Layer)

### 2.1 VerificationConfigProvider

```mermaid
graph LR
    A["Application Server<br/>(DB 조회)"]
    
    B["VerificationConfigProvider<br/>(Interface)"]
    
    C["did-verifier-sdk<br/>(Core Service 호출 시<br/>사용)"]
    
    D["VerificationConfigProviderImpl<br/>(구현체)"]
    
    E["PolicyRepository<br/>PayloadRepository<br/>VpFilterRepository<br/>VpProcessRepository"]
    
    F["DB"]
    
    D -->|구현| B
    B -->|사용| C
    D -->|조회| E
    E -->|CRUD| F
    A -.->|개발| D
    
    style B fill:#e8f5e9,stroke:#4CAF50
    style D fill:#c8e6c9,stroke:#2e7d32
    style C fill:#e3f2fd,stroke:#1976D2
```

**Interface 정의:**
```java
// did-verifier-sdk에 정의
public interface VerificationConfigProvider {
    
    // 4 메서드만 필요
    VerifyOfferPayloadDto getVerifyOfferPayloadDto(String policyId);
    
    VerifyProfileDto getVerifyProfileDto(String policyId);
    
    FilterConfigDto getFilterConfig(String policyId);
    
    ProcessConfigDto getProcessConfig(String policyId);
}

// DTO들 (SDK 정의, Application이 구현할 때 채워줌)
@Data
class VerifyOfferPayloadDto {
    String service;
    String device;
    String mode;
    List<String> endpoints;
    Integer validSecond;
    String offerType;
}

@Data
class VerifyProfileDto {
    String policyId;
    FilterConfigDto filter;
    ProcessConfigDto process;
}
```

---

## 3. 함수별 호출 흐름 개선

### 3.1 requestVpOfferbyQR (개선된 흐름)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant Controller as VerifierController
    participant AppService as TransactionServiceImpl
    participant SDK as SDK: VerifierService
    participant Provider as VerificationConfigProviderImpl
    participant Repo as Repository
    participant DB as Database
    
    Client->>Controller: POST /request-offer<br/>{ policyId, service, device }
    activate Controller
    
    Note over Controller: 1️⃣ Application Logic<br/>Database 조회
    
    Controller->>Provider: getVerifyOfferPayloadDto(policyId)
    activate Provider
    Provider->>Repo: PolicyRepository.findByPolicyId()
    Repo->>DB: SELECT policy
    DB-->>Repo: policy entity
    Provider->>Repo: PayloadRepository.findByPayloadId()
    Repo->>DB: SELECT payload
    DB-->>Repo: payload entity
    Provider->>Provider: entity → DTO 변환
    Provider-->>Controller: VerifyOfferPayloadDto
    deactivate Provider
    
    Note over Controller: 2️⃣ SDK 호출<br/>Protocol Logic 실행
    
    Controller->>SDK: createVpOfferPayload(payloadDto)
    activate SDK
    SDK->>SDK: payload 데이터로<br/>VpOfferPayload 생성<br/>(순수 함수)
    SDK-->>Controller: VpOfferPayload with offerId
    deactivate SDK
    
    Note over Controller: 3️⃣ Application Logic<br/>저장 및 응답
    
    Controller->>AppService: createAndSaveTransaction()
    AppService->>Repo: TransactionRepository.save()
    Repo->>DB: INSERT transaction
    
    Controller->>Repo: VpOfferRepository.save(vpOffer)
    Repo->>DB: INSERT vp_offer
    
    Controller-->>Client: 200 OK<br/>{ txId, payload }
    deactivate Controller
    
    Note over Controller: ✅ 개선점<br/>- Protocol과 Application 분리<br/>- 2-3개 Interface 의존만<br/>- Repository는 Application만 사용<br/>- SDK는 순수 함수
```

### 3.2 requestProfile (개선된 흐름)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant Controller as VerifierController
    participant AppService as E2EQueryServiceImpl
    participant SDK as SDK: VerifierService
    participant Provider as VerificationConfigProviderImpl<br/>& VerifierInfoProviderImpl
    participant Repo as Repository
    participant Crypto as 암호화 유틸
    
    Client->>Controller: POST /profile<br/>{ txId/offerId }
    activate Controller
    
    Note over Controller: 1️⃣ Transaction 검증<br/>(Application)
    
    Controller->>Repo: TransactionRepository.findByTxId()
    Repo-->>Controller: transaction
    
    Note over Controller: 2️⃣ Config 조회<br/>(Application → Provider)
    
    Controller->>Provider: getVerifyProfileDto(policyId)
    Provider-->>Controller: VerifyProfileDto<br/>(Filter, Process 포함)
    
    Note over Controller: 3️⃣ E2E 키 생성<br/>(Application)
    
    Controller->>Crypto: generateEcKeyPair()
    Crypto-->>Controller: KeyPair
    
    Controller->>AppService: saveE2eSession(keyPair)
    AppService->>Repo: E2eRepository.save()
    
    Note over Controller: 4️⃣ SDK 호출<br/>(Protocol Logic)
    
    Controller->>SDK: createVerifyProfile(profileDto, keyPair)
    activate SDK
    SDK->>SDK: Profile 구조 생성<br/>(순수 함수)
    SDK-->>Controller: VerifyProfile
    deactivate SDK
    
    Note over Controller: 5️⃣ Profile Proof 서명<br/>(Application)
    
    Controller->>Repo: Wallet.generateSignature()
    Repo-->>Controller: proof
    
    Note over Controller: 6️⃣ 저장 및 응답<br/>(Application)
    
    Controller->>Repo: VpProfileRepository.save()
    Controller-->>Client: 200 OK<br/>{ profile, txId }
    deactivate Controller
```

### 3.3 requestVerify (개선된 흐름)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant Controller as VerifierController
    participant SDK as SDK: VerifierService
    participant Crypto as 암호화/검증
    participant Provider as StorageService
    participant Repo as Repository
    
    Client->>Controller: POST /verify<br/>{ encVp, accE2e, txId }
    activate Controller
    
    Note over Controller: 1️⃣ Transaction 검증<br/>(Application)
    Controller->>Repo: findByTxId()
    Repo-->>Controller: transaction, e2e_session
    
    Note over Controller: 2️⃣ AccE2e 검증<br/>(SDK Protocol)
    Controller->>SDK: verifyAccE2eProof(accE2e)
    activate SDK
    SDK->>Provider: findDidDoc(holder)
    Provider-->>SDK: DidDocument
    SDK->>Crypto: verifySignature()
    Crypto-->>SDK: valid/invalid
    SDK-->>Controller: OK or Exception
    deactivate SDK
    
    Note over Controller: 3️⃣ VP 복호화<br/>(Application + 암호화 유틸)
    Controller->>Crypto: generateSharedSecret()
    Crypto-->>Controller: symmetricKey
    Controller->>Crypto: decrypt(encVp, key)
    Crypto-->>Controller: decryptedVp
    
    Note over Controller: 4️⃣ VP 검증<br/>(SDK Protocol)
    Controller->>SDK: verifyPresentation(vp)
    activate SDK
    SDK->>SDK: validateAuthType()
    SDK->>SDK: validateNonce()
    SDK->>SDK: verifyVpHolderProof()
    loop for each VC
        SDK->>Provider: getVcMeta()
        Provider-->>SDK: vc meta
        SDK->>SDK: verifyVcProof()
    end
    SDK-->>Controller: VerificationResult
    deactivate SDK
    
    Note over Controller: 5️⃣ 저장 및 응답<br/>(Application)
    Controller->>Repo: VpSubmitRepository.save()
    Controller->>Repo: TransactionRepository.update(COMPLETED)
    Controller-->>Client: 200 OK
    deactivate Controller
```

### 3.4 confirmVerify (개선된 흐름 - Plugin 패턴)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant Controller as VerifierController
    participant SDK as SDK: VerifierService
    participant Registry as OfferTypeHandlerRegistry
    participant Handler as Handler(구현체)<br/>예: StandardVerifyOfferHandler
    participant Repo as Repository
    
    Client->>Controller: POST /confirm<br/>{ offerId }
    activate Controller
    
    Note over Controller: 1️⃣ VP 데이터 조회<br/>(Application)
    Controller->>Repo: VpSubmitRepository.findByOfferId()
    Repo-->>Controller: vpData, vpOfferType
    
    Note over Controller: 2️⃣ SDK 호출<br/>(Protocol Logic)
    Controller->>SDK: confirmVerification(vpData, offerType)
    activate SDK
    
    Note over SDK: OfferType별 Handler 선택<br/>(Plugin 패턴)
    SDK->>Registry: getHandler(offerType)
    Registry-->>SDK: Handler 반환
    
    SDK->>Handler: extractClaims(vpData)
    activate Handler
    Handler->>Handler: 클레임 추출<br/>(OfferType별 로직)
    Handler-->>SDK: List<Claim>
    deactivate Handler
    
    SDK-->>Controller: VerificationResult with Claims
    deactivate SDK
    
    Note over Controller: 3️⃣ 응답<br/>(Application)
    Controller-->>Client: 200 OK<br/>{ claims, result }
    deactivate Controller
```

---

## 4. Interface 목록 및 책임

### 4.1 SDK가 정의하는 Interface (7개)

```mermaid
graph TB
    subgraph SDK["did-verifier-sdk"]
        I1["1️⃣ VerificationConfigProvider<br/>(Policy/Payload 조회)"]
        I2["2️⃣ VerifierInfoProvider<br/>(Verifier DID/인증서)"]
        I3["3️⃣ E2eSessionProvider<br/>(E2E 세션 저장/조회)"]
        I4["4️⃣ StorageService<br/>(DidDoc, VC 조회)"]
        I5["5️⃣ OfferTypeHandler<br/>(플러그인 확장)"]
        I6["6️⃣ TransactionService<br/>(Transaction 관리)"]
        I7["7️⃣ VpOfferQueryService<br/>(VP Offer 조회)"]
    end
    
    subgraph App["Verifier Application Server"]
        Impl1["VerificationConfigProviderImpl"]
        Impl2["VerifierInfoProviderImpl"]
        Impl3["E2eSessionProviderImpl"]
        Impl4["StorageServiceImpl"]
        Impl5["StandardVerifyOfferHandler<br/>+ 커스텀 Handlers"]
        Impl6["TransactionServiceImpl"]
        Impl7["VpOfferQueryServiceImpl"]
    end
    
    I1 -.->|구현| Impl1
    I2 -.->|구현| Impl2
    I3 -.->|구현| Impl3
    I4 -.->|구현| Impl4
    I5 -.->|구현| Impl5
    I6 -.->|구현| Impl6
    I7 -.->|구현| Impl7
    
    style SDK fill:#e3f2fd
    style App fill:#f0fff0
```

### 4.2 각 Interface의 책임

| Interface | 책임 | Application 구현 |
|-----------|------|-----------------|
| **VerificationConfigProvider** | Policy/Payload/Filter/Process 조회 | DB 또는 Admin API 호출 |
| **VerifierInfoProvider** | Verifier DID, 인증서 조회 | YAML 설정 또는 DB |
| **E2eSessionProvider** | E2E 세션 저장/조회 | Redis 또는 DB |
| **StorageService** | DidDoc, VC, VcMeta 조회 | Blockchain 또는 Repository |
| **OfferTypeHandler** | OfferType별 클레임 추출 (플러그인) | 각 타입별 구현 |
| **TransactionService** | Transaction 생성/조회/상태 변경 | Repository 사용 |
| **VpOfferQueryService** | VP Offer 조회/저장 | Repository 사용 |

---

## 5. 의존성 감소 비교

### 5.1 Before (AS-IS)

```
VerifierServiceImpl
├─ PolicyRepository
├─ PayloadRepository
├─ PolicyProfileRepository
├─ VpFilterRepository
├─ VpProcessRepository
├─ VpOfferRepository
├─ VpProfileRepository
├─ VpSubmitRepository
├─ E2eQueryService
├─ TransactionService
├─ FileWalletService
├─ StorageService
├─ DidDocService
├─ VerifierInfoQueryService
├─ ZkpPolicyProfileRepository
├─ ZkpProofRequestRepository
├─ ObjectMapper
└─ ... 더 있음

⚠️ 총 17+ 의존성
```

### 5.2 After (TO-BE)

```
SDK: VerifierService
├─ VerificationConfigProvider (Interface)
├─ VerifierInfoProvider (Interface)
├─ E2eSessionProvider (Interface)
├─ StorageService (Interface)
└─ 비즈니스 로직

✅ 4개 Interface 의존만

Application: VerifierController
├─ VerifierService (SDK)
├─ TransactionServiceImpl
├─ VpOfferQueryService
├─ VpOfferRepository
├─ VpProfileRepository
├─ VpSubmitRepository
└─ ... 기타 Repository 5-6개

✅ 명확하고 관리 가능한 의존성
```

---

## 6. 코드 라인 수 비교

```mermaid
bar
    title "코드 라인 수 변화"
    x-axis [VerifierServiceImpl, Controller, Total]
    y-axis "Lines" 0 1200
    bar [1000, 200, 1200] "AS-IS"
    bar [300, 100, 400] "TO-BE"
```

| 항목 | AS-IS | TO-BE | 감소 |
|------|-------|-------|------|
| VerifierServiceImpl | 1000+ lines | 300 lines (SDK) | 70% ↓ |
| VerifierController | 200 lines | 100 lines | 50% ↓ |
| 의존성 개수 | 17+ | 4 (Interface) | 76% ↓ |
| 테스트 Mock 필요 | 17개 | 4개 | 76% ↓ |

---

## 7. 실제 변환 예시

### 7.1 requestVpOfferbyQR 변환

#### BEFORE (현재)

```java
@Service
public class VerifierServiceImpl {
    @Autowired private PolicyRepository policyRepository;
    @Autowired private PayloadRepository payloadRepository;
    @Autowired private VpOfferRepository vpOfferRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private VpOfferQueryService vpOfferQueryService;
    // ... 12개 더
    
    @Override
    public RequestOfferResDto requestVpOfferbyQR(RequestOfferReqDto reqDto) {
        // 1. DB에서 Policy 조회
        Policy policy = policyRepository.findByPolicyId(reqDto.getPolicyId())
            .orElseThrow(() -> new OpenDidException(ErrorCode.VP_POLICY_NOT_FOUND));
        
        // 2. DB에서 Payload 조회
        Payload payload = payloadRepository.findByPayloadId(policy.getPayloadId())
            .orElseThrow(() -> new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND));
        
        // 3. Transaction 생성
        Transaction transaction = createAndSaveTransaction();
        
        // 4. Offer 생성 (여기부터 Protocol Logic)
        VerifyOfferPayload vpPayload = policyToVerifyOfferPayload(payload);
        String offerId = UUID.randomUUID().toString();
        vpPayload.setOfferId(offerId);
        
        // 5. VpOffer 저장
        vpOfferQueryService.insertVpOffer(VpOffer.builder()
            .transactionId(transaction.getId())
            .offerId(offerId)
            .vpPolicyId(policy.getPolicyId())
            .offerType(vpPayload.getType().toString())
            .payload(JsonUtil.serializeToJson(vpPayload))
            .build());
        
        return RequestOfferResDto.builder()
            .txId(transaction.getTxId())
            .payload(vpPayload)
            .build();
    }
}
```

#### AFTER (개선)

```java
// ========== SDK: did-verifier-sdk ==========
@Service
@RequiredArgsConstructor
public class VerifierService {
    private final VerificationConfigProvider configProvider;  // 2개 Interface만
    private final VerifierInfoProvider infoProvider;
    
    public VerifyOfferPayload createVpOfferPayload(
            VerifyOfferPayloadDto payloadDto) {
        
        // 순수 함수: 데이터 가공만
        return VerifyOfferPayload.builder()
            .service(payloadDto.getService())
            .device(payloadDto.getDevice())
            .mode(payloadDto.getMode())
            .endpoints(payloadDto.getEndpoints())
            .locked(false)
            .validUntil(calculateValidUntil(payloadDto.getValidSecond()))
            .type(payloadDto.getOfferType())
            .build();
    }
    
    private String calculateValidUntil(Integer validSeconds) {
        return Instant.now()
            .plusSeconds(validSeconds)
            .toString();
    }
}

// ========== Application Server: VerifierController ==========
@RestController
@RequiredArgsConstructor
public class VerifierController {
    private final VerifierService verifierService;  // SDK
    private final VerificationConfigProvider configProvider;  // Interface 의존
    private final TransactionService transactionService;
    private final VpOfferRepository vpOfferRepository;
    private final VpOfferQueryService vpOfferQueryService;
    
    @PostMapping("/request-offer")
    public ResponseEntity<RequestOfferResDto> requestVpOfferbyQR(
            @RequestBody RequestOfferReqDto reqDto) {
        
        try {
            // 1️⃣ Application Logic: 설정 조회
            VerifyOfferPayloadDto payloadDto = configProvider
                .getVerifyOfferPayloadDto(reqDto.getPolicyId());
            
            // 2️⃣ SDK 호출: Protocol Logic
            VerifyOfferPayload payload = verifierService
                .createVpOfferPayload(payloadDto);
            
            // 3️⃣ Application Logic: 저장
            Transaction transaction = transactionService
                .insertTransaction(Transaction.builder()
                    .type(TransactionType.VP_SUBMIT)
                    .txId(UUID.randomUUID().toString())
                    .status(TransactionStatus.PENDING)
                    .build());
            
            String vpOfferId = UUID.randomUUID().toString();
            payload.setOfferId(vpOfferId);
            
            vpOfferQueryService.insertVpOffer(VpOffer.builder()
                .transactionId(transaction.getId())
                .offerId(vpOfferId)
                .payload(JsonUtil.serializeToJson(payload))
                .build());
            
            return ResponseEntity.ok(RequestOfferResDto.builder()
                .txId(transaction.getTxId())
                .payload(payload)
                .build());
                
        } catch (OpenDidException e) {
            log.error("Error: {}", e.getErrorCode().getMessage());
            throw e;
        }
    }
}

// ========== Application Server: VerificationConfigProviderImpl ==========
@Service
@RequiredArgsConstructor
public class VerificationConfigProviderImpl 
        implements VerificationConfigProvider {
    
    private final PolicyRepository policyRepository;
    private final PayloadRepository payloadRepository;
    private final VpFilterRepository vpFilterRepository;
    private final VpProcessRepository vpProcessRepository;
    
    @Override
    public VerifyOfferPayloadDto getVerifyOfferPayloadDto(String policyId) {
        // DB에서 조회
        Policy policy = policyRepository.findByPolicyId(policyId)
            .orElseThrow(() -> new OpenDidException(ErrorCode.VP_POLICY_NOT_FOUND));
        
        Payload payload = payloadRepository.findByPayloadId(policy.getPayloadId())
            .orElseThrow(() -> new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND));
        
        // Entity → DTO 변환
        return VerifyOfferPayloadDto.builder()
            .service(payload.getService())
            .device(payload.getDevice())
            .mode(payload.getMode().toString())
            .endpoints(parseEndpoints(payload.getEndpoints()))
            .validSecond(payload.getValidSecond())
            .offerType(payload.getOfferType().toString())
            .build();
    }
}
```

---

## 8. 다음 단계

### 8.1 작업 순서

```mermaid
graph LR
    A["1️⃣ Interface 정의<br/>(SDK - did-verifier-sdk)"]
    B["2️⃣ DTO 정의<br/>(SDK)"]
    C["3️⃣ Core Service 구현<br/>(SDK - VerifierService)"]
    D["4️⃣ 구현체 작성<br/>(Application)"]
    E["5️⃣ Controller 리팩토링<br/>(Application)"]
    F["6️⃣ 테스트<br/>(단위 + 통합)"]
    
    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    
    style A fill:#fff9c4
    style B fill:#fff9c4
    style C fill:#fff9c4
    style D fill:#c8e6c9
    style E fill:#c8e6c9
    style F fill:#a5d6a7,color:#fff
```

### 8.2 파일 생성 계획

```
did-verifier-sdk/src/main/java/org/omnione/did/sdk/verifier/
├── integration/
│   └── provider/
│       ├── VerificationConfigProvider.java
│       ├── VerifierInfoProvider.java
│       ├── E2eSessionProvider.java
│       ├── StorageService.java
│       ├── OfferTypeHandler.java
│       ├── TransactionService.java
│       └── VpOfferQueryService.java
├── core/
│   └── service/
│       ├── VerifierService.java
│       ├── OfferTypeHandlerRegistry.java
│       └── ...
└── common/
    ├── dto/
    │   ├── VerifyOfferPayloadDto.java
    │   ├── VerifyProfileDto.java
    │   ├── FilterConfigDto.java
    │   ├── ProcessConfigDto.java
    │   └── ...
    └── enums/
        └── ...

did-verifier-server/src/main/java/org/omnione/did/verifier/v1/agent/
├── service/
│   ├── provider/
│   │   ├── VerificationConfigProviderImpl.java
│   │   ├── VerifierInfoProviderImpl.java
│   │   ├── E2eSessionProviderImpl.java
│   │   ├── StorageServiceImpl.java
│   │   └── ...
│   ├── handler/
│   │   ├── StandardVerifyOfferHandler.java
│   │   └── CustomOfferHandlers.java
│   └── VerifierServiceImpl.java (리팩토링)
└── controller/
    └── VerifierController.java (리팩토링)
```

---

## 9. 요약: 개선 효과

```mermaid
graph LR
    Before["AS-IS<br/>- 1000+ lines<br/>- 17+ 의존성<br/>- 복잡한 구조"]
    After["TO-BE<br/>- 300 lines (SDK)<br/>- 4 Interface 의존<br/>- 명확한 책임"]
    
    Before -->|SDK화| After
    
    Benefits["✅ 효과<br/>- 75% 코드 감소<br/>- 76% 의존성 감소<br/>- 테스트 용이<br/>- 재사용 가능<br/>- 확장성 ↑"]
    
    After --> Benefits
    
    style Before fill:#ffcdd2
    style After fill:#c8e6c9
    style Benefits fill:#a5d6a7,color:#fff
```

---

이제 이 설계를 기반으로 실제 코드를 작성하면 됩니다!
궁금한 점이나 수정할 부분이 있으면 언제든 말씀해주세요. 🚀
