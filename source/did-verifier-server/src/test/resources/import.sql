-- test 프로파일은 liquibase.enabled=false(schema-only, hibernate ddl-auto=create-drop)라
-- src/main/resources/db/changelog의 oid4vp_config seed(protocol-seed_oid4vp_config.xml 등)가
-- 적용되지 않는다. VerifierConfigService(SDK)는 @PostConstruct에서 이 행을 즉시 읽어 실패하면
-- RuntimeException을 던져 전체 ApplicationContext 부팅이 깨지므로, 최소 유효 설정을 직접 심는다.
-- 값은 set.4/protocol-add_oid4vp_config_encryption.xml(최신 seed 리터럴, encryption 필드 포함)과 동일하다.
-- Hibernate의 기본 legacy import 파일(hibernate.hbm2ddl.import_files 기본값 "import.sql")이라
-- create-drop 스키마 생성과 같은 SessionFactory 부트스트랩 호출 안에서 동기적으로 실행되어,
-- Spring Boot의 이벤트 기반 data.sql보다 이 조합(JPA 단독, Liquibase 비활성)에서 순서가 확실하다.
INSERT INTO oid4vp_config (type, config, created_at) VALUES ('OID4VP', '{"baseUrl":"http://127.0.0.1:8092","clientName":"OpenDID Verifier","invocationScheme":"openid4vp://","clientId":{"scheme":"decentralized_identifier","value":"did:omn:verifier"},"session":{"sessionTtl":300000},"endpoints":{"response":"/oid4vp/response","request":"/oid4vp/request"},"clientMetadata":{"vpFormatsSupported":{"dc+sd-jwt":{},"opendid_vc":{},"mso_mdoc":{"alg_values":["ES256"]}}},"crypto":{"vpTokenEncryptionKey":"PcYKARaqs/LMVoIG3kzTV1BHDDDSJrmNJKn6mOTMhu8="},"encryption":{"alg":"ECDH-ES","enc":"A256GCM"},"verification":{"skipX5cChainValidation":false,"enforceClaimConstraints":false}}', CURRENT_TIMESTAMP);
