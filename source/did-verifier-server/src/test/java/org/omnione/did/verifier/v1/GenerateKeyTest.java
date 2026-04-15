package org.omnione.did.verifier.v1;

import org.junit.jupiter.api.Test;
import org.omnione.did.wallet.key.WalletManagerFactory;
import org.omnione.did.wallet.key.WalletManagerInterface;
import org.omnione.did.wallet.key.data.CryptoKeyPairInfo;

class GenerateKeyTest {

    static final String WALLET_PATH = "/Users/jinhwan-notebook/workspace/did-orchestrator-server/source/did-orchestrator-server/jars/Verifier/verifier.wallet";
    static final String PASSWORD = "omnioneopendid12!@";

    @Test
    void generateSecp256r1Key() throws Exception {
        WalletManagerInterface walletManager = WalletManagerFactory.getWalletManager(WalletManagerFactory.WalletManagerType.FILE);
        walletManager.connect(WALLET_PATH, PASSWORD.toCharArray());

        String keyId = "testSecp256r1";
        if (!walletManager.isExistKey(keyId)) {
            walletManager.generateRandomKey(keyId, CryptoKeyPairInfo.KeyAlgorithmType.SECP256r1);
            System.out.println("키 생성 완료: " + keyId);
        } else {
            System.out.println("키 이미 존재: " + keyId);
        }

        var keyElement = walletManager.getKeyElement(keyId);
        System.out.println("알고리즘: " + keyElement.getAlgorithm());
        System.out.println("공개키: " + keyElement.getPublicKey());
        System.out.println("개인키: " + keyElement.getPrivateKey());
    }
}
