# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenDID Verifier Server - DID(Decentralized Identifier) 검증 시스템. Spring Boot 백엔드와 React 관리 콘솔로 구성된 풀스택 애플리케이션.

## Build Commands

### Backend (source/did-verifier-server/)

```bash
# 프로덕션 JAR 빌드 (프론트엔드 포함)
./gradlew bootJar

# 프론트엔드 빌드 제외하고 백엔드만 빌드
./gradlew bootJar -DskipFrontendBuild=true

# 테스트 전체 실행
./gradlew test

# 단일 테스트 실행
./gradlew test --tests "org.omnione.did.verifier.v1.VerifierSDKIntegrationTest"
./gradlew test --tests "*.VpVerificationServiceTest"

# 클린 빌드
./gradlew clean build

# 프론트엔드만 빌드
./gradlew npm_build

# 의존성 라이선스 리포트 생성
./gradlew licenseReport
```

### Frontend (source/did-verifier-admin/frontend/)

```bash
npm install          # 의존성 설치
npm run dev          # 개발 서버 실행 (Vite)
npm run build        # TypeScript 빌드 + Vite 번들링
npm run preview      # 프로덕션 빌드 미리보기
```

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.1, QueryDSL 5.0.0
- **Frontend**: React 19, TypeScript 5, Vite 6, MUI v6, Toolpad Core v0.12.0, React Router 7
- **Database**: PostgreSQL (Liquibase 마이그레이션), H2 (테스트)
- **Build**: Gradle 7.0+ (Composite Build)

## Architecture

```
source/
├── did-verifier-server/           # 백엔드 메인 애플리케이션 (Composite Build)
│   ├── src/main/java/org/omnione/did/
│   │   ├── VerifierApplication.java
│   │   ├── base/                      # 기반 인프라
│   │   │   ├── aop/                   # ControllerLogAspects
│   │   │   ├── config/                # Security, JPA, OpenFeign, QueryDSL 설정
│   │   │   ├── controller/            # GlobalControllerAdvice
│   │   │   ├── datamodel/             # 도메인 데이터 모델 & Enum
│   │   │   ├── db/                    # JPA 엔티티, Repository, QueryDSL Custom
│   │   │   ├── exception/             # OpenDidException
│   │   │   └── property/              # @ConfigurationProperties 바인딩
│   │   └── verifier/v1/
│   │       ├── admin/                 # Policy 및 설정 관리 API
│   │       ├── agent/                 # 핵심 검증 비즈니스 로직
│   │       │   ├── api/               # OpenFeign 클라이언트 (TAS, Blockchain, LSS)
│   │       │   ├── adapter/           # SDK SPI 구현체 (7개 Adapter)
│   │       │   ├── controller/        # REST 컨트롤러
│   │       │   ├── service/           # ApplicationVerifierServiceImpl
│   │       │   │   └── sample/        # 샘플 구현체
│   │       │   └── config/
│   │       └── common/                # 공통 서비스/DTO
│   ├── verifier-sdk/                  # SDK 모듈 (Composite Build, JAR 라이브러리)
│   │   └── src/main/java/org/omnione/did/verifier/v1/
│   │       ├── api/                   # 7개 SPI 인터페이스
│   │       ├── service/               # 6개 핵심 서비스 (VP 4개 + ZKP 2개)
│   │       ├── dto/                   # 16개 DTO
│   │       └── exception/             # 7개 예외 클래스
│   └── libs/                          # OpenDID 사전 빌드 JAR (7개)
│       └── did-*-sdk-server-2.0.0.jar
│
└── did-verifier-admin/frontend/   # React 관리 콘솔
```

## SDK & Adapter 패턴

`verifier-sdk`는 SPI 인터페이스를 정의하고, 메인 애플리케이션의 `agent/adapter/` 패키지가 7개 SPI 구현체(Adapter)를 제공한다. `ApplicationVerifierServiceImpl`이 이 Adapter들을 조합해 VP/ZKP 검증 흐름을 구성한다.

외부 시스템 연동은 `agent/api/` 패키지의 OpenFeign 클라이언트로 처리:
- **TAS** (Trust Anchor Service): 신뢰 앵커 조회
- **Blockchain**: DID Document 조회
- **LSS** (Label Scanning Service)

## Configuration

- 기본 포트: 8092
- 데이터베이스: PostgreSQL (localhost:5430), DB명: `verifier`, user: `omn`
- 활성 프로파일 그룹: `sample` (기본, H2/로컬), `dev`, `lss`
- Swagger UI: `/swagger-ui.html`
- E2E 암호화: Secp256r1, AES-256-CBC
- LSS URL (lss 프로파일): http://127.0.0.1:8098

주요 설정 파일:
- `application.yml` - 포트, Jackson, Liquibase
- `application-verifier.yml` - Verifier DID, TAS URL
- `application-databases-sample.yml` - 샘플 DB 연결 정보
- `application-lss.yml` - LSS 서비스 URL

## Development Notes

- Gradle 빌드 시 Node.js v22.9.0 자동 다운로드 후 프론트엔드 빌드 → `src/main/resources/static`으로 복사
- 프론트엔드 개발 서버는 백엔드(8092)로 프록시 설정됨
- **QueryDSL**: 엔티티 변경 시 반드시 빌드 실행 필요 (Q클래스 재생성)
- Liquibase 마이그레이션 진입점: `src/main/resources/db/changelog/master.xml`
- `verifier-sdk`는 `dependencyManagement`만 사용 (Spring Boot plugin `apply false`)

## Protocol Routing Design (진행 중)

`PROTOCOL_ROUTING_DESIGN.md` 참조. DID VP 프로토콜과 OID4VP 프로토콜을 Policy 기반으로 통합하는 설계 진행 중.

핵심 결정 사항:
- HYBRID 모드 채택 - `protocol_config` 테이블로 Policy별 프로토콜 타입 관리
- `/v2` API 신설 (기존 `/v1` 유지), 통합 진입점 `/verifier/api/v2/initiate`
- `ProtocolHandler` 인터페이스로 DID VP / OID4VP 의존성 분리
- 목표 패키지: `verifier/protocol/` (통합 레이어), `verifier/didvp/`, `verifier/oid4vp/`

## Documentation

- API 문서: `docs/api/Verifier_API_ko.md`
- 설치 가이드: `docs/installation/`
- 에러 코드: `docs/errorCode/`
- DB 스키마: `docs/db/`
- SDK 가이드: `source/did-verifier-server/verifier-sdk/docs/SDK_GUIDE.md`
