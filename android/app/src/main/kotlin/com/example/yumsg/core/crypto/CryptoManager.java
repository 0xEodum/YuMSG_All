package com.example.yumsg.core.crypto;

import android.util.Log;
import android.util.Base64;

import com.example.yumsg.core.data.*;
import com.example.yumsg.core.enums.*;
import com.example.yumsg.core.storage.SharedPreferencesManager;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.engines.ChaCha7539Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.pqc.crypto.cmce.*;
import org.bouncycastle.pqc.crypto.ntru.*;
import org.bouncycastle.pqc.crypto.bike.*;
import org.bouncycastle.pqc.crypto.hqc.*;
import org.bouncycastle.pqc.crypto.saber.*;
import org.bouncycastle.pqc.crypto.crystals.kyber.*;
import org.bouncycastle.pqc.crypto.frodo.*;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.RainbowParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.Key;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CryptoManager - Adapted Implementation for YuMSG Architecture
 * 
 * Provides quantum-resistant cryptographic operations using BouncyCastle.
 * Supports multiple post-quantum algorithms for KEM, encryption, and signatures.
 * 
 * Key Features:
 * - Post-quantum KEM algorithms (NTRU, KYBER, BIKE, HQC, SABER, MCELIECE, FRODO)
 * - Digital signatures (FALCON, DILITHIUM, RAINBOW)
 * - Symmetric encryption (AES-256-GCM, SALSA20, CHACHA20)
 * - File encryption/decryption
 * - Key management and serialization
 * - Thread-safe operations
 * - Integration with SharedPreferencesManager
 */
public class CryptoManager {
    private static final String TAG = "CryptoManager";
    
    // Singleton instance
    private static volatile CryptoManager instance;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    
    // Core components
    private SecureRandom secureRandom;
    private final Map<String, AlgorithmInfo> algorithmInfoCache = new ConcurrentHashMap<>();
    
    // Supported algorithms
    private static final List<String> SUPPORTED_KEM_ALGORITHMS = Arrays.asList(
        "NTRU", "KYBER", "BIKE", "HQC", "SABER", "MCELIECE", "FRODO"
    );
    
    private static final List<String> SUPPORTED_SYMMETRIC_ALGORITHMS = Arrays.asList(
        "AES-256", "SALSA20", "CHACHA20"
    );
    
    private static final List<String> SUPPORTED_SIGNATURE_ALGORITHMS = Arrays.asList(
        "FALCON", "DILITHIUM", "RAINBOW"
    );
    
    /**
     * Private constructor for singleton pattern
     */
    private CryptoManager() {
        Log.d(TAG, "CryptoManager instance created");
    }
    
    /**
     * Get singleton instance
     */
    public static CryptoManager getInstance() {
        if (instance == null) {
            synchronized (CryptoManager.class) {
                if (instance == null) {
                    instance = new CryptoManager();
                }
            }
        }
        return instance;
    }
    
    // ===========================
    // LIFECYCLE METHODS
    // ===========================
    
    /**
     * Initialize the crypto manager
     */
    public boolean initialize() {
        lock.writeLock().lock();
        try {
            if (isInitialized.get()) {
                Log.w(TAG, "CryptoManager already initialized");
                return true;
            }
            
            Log.d(TAG, "Initializing CryptoManager");
            
            // Initialize secure random
            secureRandom = new SecureRandom();
            secureRandom.nextBytes(new byte[32]); // Seed the generator
            
            // Initialize algorithm info cache
            initializeAlgorithmInfoCache();
            
            isInitialized.set(true);
            Log.i(TAG, "CryptoManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CryptoManager", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cleanup crypto manager resources
     */
    public void cleanup() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Cleaning up CryptoManager resources");
            
            isInitialized.set(false);
            algorithmInfoCache.clear();
            
            if (secureRandom != null) {
                secureRandom = null;
            }
            
            Log.i(TAG, "CryptoManager resources cleaned up");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if crypto manager is initialized
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }
    
    // ===========================
    // KEY GENERATION
    // ===========================
    
    /**
     * Generate KEM key pair with specified algorithm
     */
    public AsymmetricCipherKeyPair generateKEMKeyPair(String algorithm) {
        checkInitialized();
        
        if (algorithm == null || !SUPPORTED_KEM_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported KEM algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Generating KEM key pair with algorithm: " + algorithm);
            
            AsymmetricCipherKeyPair keyPair;
            
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    keyPair = generateNTRUKeyPair();
                    break;
                case "KYBER":
                    keyPair = generateKyberKeyPair();
                    break;
                case "BIKE":
                    keyPair = generateBIKEKeyPair();
                    break;
                case "HQC":
                    keyPair = generateHQCKeyPair();
                    break;
                case "SABER":
                    keyPair = generateSABERKeyPair();
                    break;
                case "MCELIECE":
                    keyPair = generateMcElieceKeyPair();
                    break;
                case "FRODO":
                    keyPair = generateFrodoKeyPair();
                    break;
                default:
                    throw new RuntimeException("Unsupported KEM algorithm: " + algorithm);
            }
            
            Log.d(TAG, "KEM key pair generated successfully");
            return keyPair;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating KEM key pair: " + algorithm, e);
            throw new RuntimeException("Failed to generate KEM key pair", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Generate KEM key pair from preferences manager
     */
    public AsymmetricCipherKeyPair generateKEMKeyPairFromPreferences(SharedPreferencesManager preferencesManager) {
        checkInitialized();
        
        if (preferencesManager == null) {
            throw new IllegalArgumentException("SharedPreferencesManager cannot be null");
        }
        
        CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();
        String kemAlgorithm = algorithms.getKemAlgorithm();
        
        Log.d(TAG, "Generating KEM key pair from preferences: " + kemAlgorithm);
        return generateKEMKeyPair(kemAlgorithm);
    }
    
    /**
     * Generate signature key pair
     */
    public KeyPair generateSignatureKeyPair(String algorithm) {
        checkInitialized();
        
        if (algorithm == null || !SUPPORTED_SIGNATURE_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported signature algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Generating signature key pair with algorithm: " + algorithm);
            
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm, "BCPQC");
            
            switch (algorithm.toUpperCase()) {
                case "FALCON":
                    keyGen.initialize(FalconParameterSpec.falcon_512, secureRandom);
                    break;
                case "DILITHIUM":
                    keyGen.initialize(DilithiumParameterSpec.dilithium3, secureRandom);
                    break;
                case "RAINBOW":
                    keyGen.initialize(RainbowParameterSpec.rainbowIIIcircumzenithal, secureRandom);
                    break;
                default:
                    throw new RuntimeException("Unsupported signature algorithm: " + algorithm);
            }
            
            KeyPair keyPair = keyGen.generateKeyPair();
            Log.d(TAG, "Signature key pair generated successfully");
            return keyPair;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating signature key pair: " + algorithm, e);
            throw new RuntimeException("Failed to generate signature key pair", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Generate organization signature keys
     */
    public KeyPair generateOrganizationSignatureKeys(String organizationName, String algorithm) {
        checkInitialized();
        
        if (organizationName == null || organizationName.trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name cannot be empty");
        }
        
        Log.i(TAG, "Generating signature keys for organization: " + organizationName);
        KeyPair keyPair = generateSignatureKeyPair(algorithm);
        Log.d(TAG, "Organization signature keys generated for: " + organizationName);
        
        return keyPair;
    }
    
    /**
     * Generate and save organization keys
     */
    public void generateAndSaveOrganizationKeys(Organization organization, CryptoAlgorithms algorithms) {
        checkInitialized();
        
        if (organization == null) {
            throw new IllegalArgumentException("Organization cannot be null");
        }
        
        if (algorithms == null) {
            throw new IllegalArgumentException("CryptoAlgorithms cannot be null");
        }
        
        Log.i(TAG, "Generating and saving keys for organization: " + organization.getName());
        
        try {
            KeyPair signatureKeyPair = generateSignatureKeyPair(algorithms.getSignatureAlgorithm());
            
            byte[] publicKeyBytes = signatureKeyToBytes(signatureKeyPair.getPublic());
            byte[] privateKeyBytes = signatureKeyToBytes(signatureKeyPair.getPrivate());
            
            organization.setUserSignaturePublicKey(publicKeyBytes);
            organization.setUserSignaturePrivateKey(privateKeyBytes);
            organization.setCryptoAlgorithms(algorithms);
            
            Log.i(TAG, "Organization keys generated and saved successfully: " + organization.getName());
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating organization keys: " + organization.getName(), e);
            throw new RuntimeException("Failed to generate organization keys", e);
        }
    }
    
    // ===========================
    // CHATKEYS MANAGEMENT
    // ===========================
    
    /**
     * Initialize chat keys with specified algorithm
     */
    public ChatKeys initializeChatKeys(String algorithm) {
        checkInitialized();
        
        Log.d(TAG, "Initializing ChatKeys with algorithm: " + algorithm);
        return createChatKeys(algorithm);
    }
    
    /**
     * Initialize chat keys from preferences manager
     */
    public ChatKeys initializeChatKeysFromPreferences(SharedPreferencesManager preferencesManager) {
        checkInitialized();
        
        if (preferencesManager == null) {
            throw new IllegalArgumentException("SharedPreferencesManager cannot be null");
        }
        
        CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();
        String kemAlgorithm = algorithms.getKemAlgorithm();
        
        Log.d(TAG, "Initializing ChatKeys from preferences: " + kemAlgorithm);
        return initializeChatKeys(kemAlgorithm);
    }
    
    /**
     * Create chat keys with specified algorithm
     */
    public ChatKeys createChatKeys(String algorithm) {
        checkInitialized();
        
        Log.d(TAG, "Creating ChatKeys with algorithm: " + algorithm);
        
        try {
            AsymmetricCipherKeyPair keyPair = generateKEMKeyPair(algorithm);
            
            byte[] publicKey = kemPublicKeyToBytes(keyPair.getPublic(), algorithm);
            byte[] privateKey = kemPrivateKeyToBytes(keyPair.getPrivate(), algorithm);
            
            ChatKeys chatKeys = new ChatKeys(algorithm);
            chatKeys.setPublicKeySelf(publicKey);
            chatKeys.setPrivateKeySelf(privateKey);
            
            Log.d(TAG, "ChatKeys created successfully with algorithm: " + algorithm);
            return chatKeys;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating ChatKeys with algorithm: " + algorithm, e);
            throw new RuntimeException("Failed to create ChatKeys", e);
        }
    }
    
    /**
     * Update chat keys with peer public key
     */
    public ChatKeys updateChatKeysWithPeerKey(ChatKeys keys, byte[] peerPublicKey) {
        if (keys == null) {
            throw new IllegalArgumentException("ChatKeys cannot be null");
        }
        
        if (peerPublicKey == null || peerPublicKey.length == 0) {
            throw new IllegalArgumentException("Peer public key cannot be empty");
        }
        
        keys.setPublicKeyPeer(peerPublicKey);
        Log.d(TAG, "ChatKeys updated with peer public key");
        return keys;
    }
    
    /**
     * Complete chat initialization after KEM exchange
     */
    public ChatKeys completeChatInitialization(ChatKeys keys, byte[] secretA, byte[] secretB) {
        checkInitialized();
        
        if (keys == null) {
            throw new IllegalArgumentException("ChatKeys cannot be null");
        }
        
        if (!keys.hasKeyPair() || !keys.hasPeerKey()) {
            throw new IllegalArgumentException("ChatKeys not ready for completion");
        }
        
        Log.d(TAG, "Completing ChatKeys initialization");
        
        try {
            byte[] symmetricKey = deriveSymmetricKey(secretA, secretB);
            keys.setSymmetricKey(symmetricKey);
            
            Log.d(TAG, "ChatKeys initialization completed successfully");
            return keys;
            
        } catch (Exception e) {
            Log.e(TAG, "Error completing ChatKeys initialization", e);
            throw new RuntimeException("Failed to complete ChatKeys initialization", e);
        }
    }
    
    /**
     * Check if chat keys are complete
     */
    public boolean isChatKeysComplete(ChatKeys keys) {
        return keys != null && keys.isComplete();
    }
    
    /**
     * Generate chat fingerprint from keys
     */
    public String generateChatFingerprintFromKeys(ChatKeys keys) {
        checkInitialized();
        
        if (keys == null || !keys.hasKeyPair() || !keys.hasPeerKey()) {
            throw new IllegalArgumentException("ChatKeys missing required keys for fingerprint generation");
        }
        
        return generateChatFingerprint(keys.getPublicKeySelf(), keys.getPublicKeyPeer());
    }
    
    /**
     * Securely clear chat keys
     */
    public void secureClearChatKeys(ChatKeys keys) {
        if (keys != null) {
            keys.secureWipe();
            Log.d(TAG, "ChatKeys securely cleared");
        }
    }
    
    // ===========================
    // KEM OPERATIONS
    // ===========================
    
    /**
     * Encapsulate secret using KEM
     */
    public SecretWithEncapsulation encapsulateSecret(byte[] publicKeyBytes, String algorithm) {
        checkInitialized();
        
        if (publicKeyBytes == null || publicKeyBytes.length == 0) {
            throw new IllegalArgumentException("Public key bytes cannot be empty");
        }
        
        if (!SUPPORTED_KEM_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported KEM algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Encapsulating secret with algorithm: " + algorithm);
            
            SecretWithEncapsulation result;
            
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    result = encapsulateNTRU(publicKeyBytes);
                    break;
                case "KYBER":
                    result = encapsulateKyber(publicKeyBytes);
                    break;
                case "BIKE":
                    result = encapsulateBIKE(publicKeyBytes);
                    break;
                case "HQC":
                    result = encapsulateHQC(publicKeyBytes);
                    break;
                case "SABER":
                    result = encapsulateSABER(publicKeyBytes);
                    break;
                case "MCELIECE":
                    result = encapsulateMcEliece(publicKeyBytes);
                    break;
                case "FRODO":
                    result = encapsulateFrodo(publicKeyBytes);
                    break;
                default:
                    throw new RuntimeException("Unsupported KEM algorithm: " + algorithm);
            }
            
            Log.d(TAG, "Secret encapsulated successfully");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error encapsulating secret: " + algorithm, e);
            throw new RuntimeException("Failed to encapsulate secret", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Extract secret from KEM capsule
     */
    public byte[] extractSecret(byte[] capsuleBytes, byte[] privateKeyBytes, String algorithm) {
        checkInitialized();
        
        if (capsuleBytes == null || privateKeyBytes == null) {
            throw new IllegalArgumentException("Capsule and private key bytes cannot be null");
        }
        
        if (!SUPPORTED_KEM_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported KEM algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Extracting secret with algorithm: " + algorithm);
            
            byte[] secret;
            
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    secret = extractNTRU(capsuleBytes, privateKeyBytes);
                    break;
                case "KYBER":
                    secret = extractKyber(capsuleBytes, privateKeyBytes);
                    break;
                case "BIKE":
                    secret = extractBIKE(capsuleBytes, privateKeyBytes);
                    break;
                case "HQC":
                    secret = extractHQC(capsuleBytes, privateKeyBytes);
                    break;
                case "SABER":
                    secret = extractSABER(capsuleBytes, privateKeyBytes);
                    break;
                case "MCELIECE":
                    secret = extractMcEliece(capsuleBytes, privateKeyBytes);
                    break;
                case "FRODO":
                    secret = extractFrodo(capsuleBytes, privateKeyBytes);
                    break;
                default:
                    throw new RuntimeException("Unsupported KEM algorithm: " + algorithm);
            }
            
            Log.d(TAG, "Secret extracted successfully");
            return secret;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting secret: " + algorithm, e);
            throw new RuntimeException("Failed to extract secret", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Derive symmetric key from two secrets
     */
    public byte[] deriveSymmetricKey(byte[] secretA, byte[] secretB) {
        checkInitialized();
        
        if (secretA == null || secretB == null) {
            throw new IllegalArgumentException("Secrets cannot be null");
        }
        
        try {
            Log.d(TAG, "Deriving symmetric key from secrets");
            
            byte[] combined = new byte[secretA.length + secretB.length];
            System.arraycopy(secretA, 0, combined, 0, secretA.length);
            System.arraycopy(secretB, 0, combined, secretA.length, secretB.length);
            
            SHA3Digest sha3 = new SHA3Digest(256);
            sha3.update(combined, 0, combined.length);
            
            byte[] symmetricKey = new byte[32]; // 256-bit key
            sha3.doFinal(symmetricKey, 0);
            
            // Secure wipe combined array
            Arrays.fill(combined, (byte) 0);
            
            Log.d(TAG, "Symmetric key derived successfully");
            return symmetricKey;
            
        } catch (Exception e) {
            Log.e(TAG, "Error deriving symmetric key", e);
            throw new RuntimeException("Failed to derive symmetric key", e);
        }
    }
    
    // ===========================
    // SYMMETRIC ENCRYPTION
    // ===========================
    
    /**
     * Encrypt message with default algorithm (AES-256)
     */
    public byte[] encryptMessage(String message, byte[] symmetricKey) {
        return encryptMessage(message, symmetricKey, "AES-256");
    }
    
    /**
     * Encrypt message with specified algorithm
     */
    public byte[] encryptMessage(String message, byte[] symmetricKey, String algorithm) {
        checkInitialized();
        
        if (message == null || symmetricKey == null) {
            throw new IllegalArgumentException("Message and symmetric key cannot be null");
        }
        
        if (!SUPPORTED_SYMMETRIC_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported symmetric algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Encrypting message with algorithm: " + algorithm);
            
            byte[] messageBytes = message.getBytes("UTF-8");
            byte[] result;
            
            switch (algorithm.toUpperCase()) {
                case "AES-256":
                    result = encryptAES256(messageBytes, symmetricKey);
                    break;
                case "SALSA20":
                    result = encryptSalsa20(messageBytes, symmetricKey);
                    break;
                case "CHACHA20":
                    result = encryptChaCha20(messageBytes, symmetricKey);
                    break;
                default:
                    throw new RuntimeException("Unsupported symmetric algorithm: " + algorithm);
            }
            
            Log.d(TAG, "Message encrypted successfully");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting message: " + algorithm, e);
            throw new RuntimeException("Failed to encrypt message", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Decrypt message with default algorithm (AES-256)
     */
    public String decryptMessage(byte[] encryptedMessage, byte[] symmetricKey) {
        return decryptMessage(encryptedMessage, symmetricKey, "AES-256");
    }
    
    /**
     * Decrypt message with specified algorithm
     */
    public String decryptMessage(byte[] encryptedMessage, byte[] symmetricKey, String algorithm) {
        checkInitialized();
        
        if (encryptedMessage == null || symmetricKey == null) {
            throw new IllegalArgumentException("Encrypted message and symmetric key cannot be null");
        }
        
        if (!SUPPORTED_SYMMETRIC_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported symmetric algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Decrypting message with algorithm: " + algorithm);
            
            byte[] decryptedBytes;
            
            switch (algorithm.toUpperCase()) {
                case "AES-256":
                    decryptedBytes = decryptAES256(encryptedMessage, symmetricKey);
                    break;
                case "SALSA20":
                    decryptedBytes = decryptSalsa20(encryptedMessage, symmetricKey);
                    break;
                case "CHACHA20":
                    decryptedBytes = decryptChaCha20(encryptedMessage, symmetricKey);
                    break;
                default:
                    throw new RuntimeException("Unsupported symmetric algorithm: " + algorithm);
            }
            
            String result = new String(decryptedBytes, "UTF-8");
            Log.d(TAG, "Message decrypted successfully");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting message: " + algorithm, e);
            throw new RuntimeException("Failed to decrypt message", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // DIGITAL SIGNATURES
    // ===========================
    
    /**
     * Sign data with default algorithm (FALCON)
     */
    public byte[] signData(byte[] data, byte[] privateSignatureKey) {
        return signData(data, privateSignatureKey, "FALCON");
    }
    
    /**
     * Sign data with specified algorithm
     */
    public byte[] signData(byte[] data, byte[] privateSignatureKey, String algorithm) {
        checkInitialized();
        
        if (data == null || privateSignatureKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }
        
        if (!SUPPORTED_SIGNATURE_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported signature algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Signing data with algorithm: " + algorithm);
            
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateSignatureKey);
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            
            Signature signer = Signature.getInstance(algorithm, "BCPQC");
            signer.initSign(privateKey);
            signer.update(data);
            
            byte[] signature = signer.sign();
            Log.d(TAG, "Data signed successfully");
            return signature;
            
        } catch (Exception e) {
            Log.e(TAG, "Error signing data: " + algorithm, e);
            throw new RuntimeException("Failed to sign data", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Verify signature
     */
    public boolean verifySignature(byte[] data, byte[] signature, byte[] publicSignatureKey, String algorithm) {
        checkInitialized();
        
        if (data == null || signature == null || publicSignatureKey == null) {
            throw new IllegalArgumentException("Data, signature, and public key cannot be null");
        }
        
        if (!SUPPORTED_SIGNATURE_ALGORITHMS.contains(algorithm.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported signature algorithm: " + algorithm);
        }
        
        lock.readLock().lock();
        try {
            Log.d(TAG, "Verifying signature with algorithm: " + algorithm);
            
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicSignatureKey);
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            
            Signature verifier = Signature.getInstance(algorithm, "BCPQC");
            verifier.initVerify(publicKey);
            verifier.update(data);
            
            boolean result = verifier.verify(signature);
            Log.d(TAG, "Signature verification result: " + result);
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying signature: " + algorithm, e);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ===========================
    // ORGANIZATION KEYS
    // ===========================
    
    /**
     * Load organization keys
     */
    public KeyPair loadOrganizationKeys(Organization organization) {
        checkInitialized();
        
        if (organization == null) {
            throw new IllegalArgumentException("Organization cannot be null");
        }
        
        if (!hasOrganizationKeys(organization)) {
            throw new IllegalArgumentException("Organization missing signature keys: " + organization.getName());
        }
        
        try {
            Log.d(TAG, "Loading organization keys: " + organization.getName());
            
            CryptoAlgorithms algorithms = organization.getCryptoAlgorithms();
            String algorithm = algorithms.getSignatureAlgorithm();
            
            byte[] publicKeyBytes = organization.getUserSignaturePublicKey();
            byte[] privateKeyBytes = organization.getUserSignaturePrivateKey();
            
            PublicKey publicKey = (PublicKey) bytesToSignatureKey(publicKeyBytes, algorithm, false);
            PrivateKey privateKey = (PrivateKey) bytesToSignatureKey(privateKeyBytes, algorithm, true);
            
            Log.d(TAG, "Organization keys loaded successfully");
            return new KeyPair(publicKey, privateKey);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading organization keys: " + organization.getName(), e);
            throw new RuntimeException("Failed to load organization keys", e);
        }
    }
    
    /**
     * Check if organization has keys
     */
    public boolean hasOrganizationKeys(Organization organization) {
        if (organization == null) {
            return false;
        }
        
        return organization.getUserSignaturePublicKey() != null &&
               organization.getUserSignaturePrivateKey() != null &&
               organization.getCryptoAlgorithms() != null &&
               organization.getCryptoAlgorithms().getSignatureAlgorithm() != null;
    }
    
    // ===========================
    // KEY SERIALIZATION
    // ===========================
    
    /**
     * Convert KEM public key to bytes
     */
    public byte[] kemPublicKeyToBytes(AsymmetricKeyParameter publicKey, String algorithm) {
        checkInitialized();
        
        try {
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    return ((NTRUPublicKeyParameters) publicKey).getEncoded();
                case "KYBER":
                    return ((KyberPublicKeyParameters) publicKey).getEncoded();
                case "BIKE":
                    return ((BIKEPublicKeyParameters) publicKey).getEncoded();
                case "HQC":
                    return ((HQCPublicKeyParameters) publicKey).getEncoded();
                case "SABER":
                    return ((SABERPublicKeyParameters) publicKey).getEncoded();
                case "MCELIECE":
                    return ((CMCEPublicKeyParameters) publicKey).getEncoded();
                case "FRODO":
                    return ((FrodoPublicKeyParameters) publicKey).getEncoded();
                default:
                    throw new RuntimeException("Unsupported algorithm for public key serialization: " + algorithm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error serializing KEM public key: " + algorithm, e);
            throw new RuntimeException("Failed to serialize KEM public key", e);
        }
    }
    
    /**
     * Convert KEM private key to bytes
     */
    public byte[] kemPrivateKeyToBytes(AsymmetricKeyParameter privateKey, String algorithm) {
        checkInitialized();
        
        try {
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    return ((NTRUPrivateKeyParameters) privateKey).getEncoded();
                case "KYBER":
                    return ((KyberPrivateKeyParameters) privateKey).getEncoded();
                case "BIKE":
                    return ((BIKEPrivateKeyParameters) privateKey).getEncoded();
                case "HQC":
                    return ((HQCPrivateKeyParameters) privateKey).getEncoded();
                case "SABER":
                    return ((SABERPrivateKeyParameters) privateKey).getEncoded();
                case "MCELIECE":
                    return ((CMCEPrivateKeyParameters) privateKey).getEncoded();
                case "FRODO":
                    return ((FrodoPrivateKeyParameters) privateKey).getEncoded();
                default:
                    throw new RuntimeException("Unsupported algorithm for private key serialization: " + algorithm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error serializing KEM private key: " + algorithm, e);
            throw new RuntimeException("Failed to serialize KEM private key", e);
        }
    }

    /**
     * Get encoded bytes for a KEM public key using the specified algorithm
     */
    public byte[] getPublicKeyBytes(AsymmetricKeyParameter publicKey, String algorithm) {
        return kemPublicKeyToBytes(publicKey, algorithm);
    }

    /**
     * Get encoded bytes for a KEM private key using the specified algorithm
     */
    public byte[] getPrivateKeyBytes(AsymmetricKeyParameter privateKey, String algorithm) {
        return kemPrivateKeyToBytes(privateKey, algorithm);
    }
    
    /**
     * Convert bytes to KEM public key
     */
    public AsymmetricKeyParameter bytesToKemPublicKey(byte[] keyBytes, String algorithm) {
        checkInitialized();
        
        try {
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    return new NTRUPublicKeyParameters(NTRUParameters.ntruhps4096821, keyBytes);
                case "KYBER":
                    return new KyberPublicKeyParameters(KyberParameters.kyber768, keyBytes);
                case "BIKE":
                    return new BIKEPublicKeyParameters(BIKEParameters.bike256, keyBytes);
                case "HQC":
                    return new HQCPublicKeyParameters(HQCParameters.hqc256, keyBytes);
                case "SABER":
                    return new SABERPublicKeyParameters(SABERParameters.firesaberkem256r3, keyBytes);
                case "MCELIECE":
                    return new CMCEPublicKeyParameters(CMCEParameters.mceliece8192128r3, keyBytes);
                case "FRODO":
                    return new FrodoPublicKeyParameters(FrodoParameters.frodokem976aes, keyBytes);
                default:
                    throw new RuntimeException("Unsupported algorithm for public key deserialization: " + algorithm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing KEM public key: " + algorithm, e);
            throw new RuntimeException("Failed to deserialize KEM public key", e);
        }
    }
    
    /**
     * Convert bytes to KEM private key
     */
    public AsymmetricKeyParameter bytesToKemPrivateKey(byte[] keyBytes, String algorithm) {
        checkInitialized();
        
        try {
            switch (algorithm.toUpperCase()) {
                case "NTRU":
                    return new NTRUPrivateKeyParameters(NTRUParameters.ntruhps4096821, keyBytes);
                case "KYBER":
                    return new KyberPrivateKeyParameters(KyberParameters.kyber768, keyBytes);
                case "BIKE":
                    return deserializeBIKEPrivateKey(keyBytes);
                case "HQC":
                    return new HQCPrivateKeyParameters(HQCParameters.hqc256, keyBytes);
                case "SABER":
                    return new SABERPrivateKeyParameters(SABERParameters.firesaberkem256r3, keyBytes);
                case "MCELIECE":
                    return new CMCEPrivateKeyParameters(CMCEParameters.mceliece8192128r3, keyBytes);
                case "FRODO":
                    return new FrodoPrivateKeyParameters(FrodoParameters.frodokem976aes, keyBytes);
                default:
                    throw new RuntimeException("Unsupported algorithm for private key deserialization: " + algorithm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing KEM private key: " + algorithm, e);
            throw new RuntimeException("Failed to deserialize KEM private key", e);
        }
    }
    
    /**
     * Convert signature key to bytes
     */
    public byte[] signatureKeyToBytes(Key key) {
        checkInitialized();
        
        try {
            return key.getEncoded();
        } catch (Exception e) {
            Log.e(TAG, "Error serializing signature key", e);
            throw new RuntimeException("Failed to serialize signature key", e);
        }
    }
    
    /**
     * Convert bytes to signature key
     */
    public Key bytesToSignatureKey(byte[] keyBytes, String algorithm, boolean isPrivate) {
        checkInitialized();
        
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
            
            if (isPrivate) {
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                return keyFactory.generatePrivate(keySpec);
            } else {
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                return keyFactory.generatePublic(keySpec);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing signature key: " + algorithm, e);
            throw new RuntimeException("Failed to deserialize signature key", e);
        }
    }
    
    // ===========================
    // UTILITIES
    // ===========================
    
    /**
     * Generate hash of data
     */
    public String generateHash(byte[] data) {
        checkInitialized();
        
        try {
            SHA3Digest sha3 = new SHA3Digest(256);
            sha3.update(data, 0, data.length);
            
            byte[] hash = new byte[32];
            sha3.doFinal(hash, 0);
            
            return Base64.encodeToString(hash, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating hash", e);
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
    
    /**
     * Generate chat fingerprint
     */
    public String generateChatFingerprint(byte[] publicKeySelf, byte[] publicKeyPeer) {
        checkInitialized();
        
        try {
            byte[] combined = new byte[publicKeySelf.length + publicKeyPeer.length];
            System.arraycopy(publicKeySelf, 0, combined, 0, publicKeySelf.length);
            System.arraycopy(publicKeyPeer, 0, combined, publicKeySelf.length, publicKeyPeer.length);
            
            SHA3Digest sha3 = new SHA3Digest(256);
            sha3.update(combined, 0, combined.length);
            
            byte[] fingerprint = new byte[32];
            sha3.doFinal(fingerprint, 0);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : fingerprint) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().toUpperCase();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating chat fingerprint", e);
            throw new RuntimeException("Failed to generate chat fingerprint", e);
        }
    }
    
    /**
     * Securely wipe byte array
     */
    public void secureWipeByteArray(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }
    
    // ===========================
    // ALGORITHM VALIDATION
    // ===========================
    
    /**
     * Validate algorithm support
     */
    public boolean validateAlgorithmSupport(String algorithm, AlgorithmType type) {
        if (algorithm == null || type == null) {
            return false;
        }
        
        switch (type) {
            case ASYMMETRIC_KEM:
                return SUPPORTED_KEM_ALGORITHMS.contains(algorithm.toUpperCase());
            case SYMMETRIC:
                return SUPPORTED_SYMMETRIC_ALGORITHMS.contains(algorithm.toUpperCase());
            case DIGITAL_SIGNATURE:
                return SUPPORTED_SIGNATURE_ALGORITHMS.contains(algorithm.toUpperCase());
            default:
                return false;
        }
    }
    
    /**
     * Check if algorithms are compatible
     */
    public boolean areAlgorithmsCompatible(CryptoAlgorithms algorithms) {
        if (algorithms == null) {
            return false;
        }
        
        return SUPPORTED_KEM_ALGORITHMS.contains(algorithms.getKemAlgorithm()) &&
               SUPPORTED_SYMMETRIC_ALGORITHMS.contains(algorithms.getSymmetricAlgorithm()) &&
               SUPPORTED_SIGNATURE_ALGORITHMS.contains(algorithms.getSignatureAlgorithm());
    }
    
    /**
     * Validate crypto algorithms
     */
    public void validateCryptoAlgorithms(CryptoAlgorithms algorithms) {
        if (algorithms == null) {
            throw new IllegalArgumentException("CryptoAlgorithms cannot be null");
        }
        
        if (!areAlgorithmsCompatible(algorithms)) {
            throw new IllegalArgumentException("Incompatible crypto algorithms: " + algorithms);
        }
        
        Log.d(TAG, "Crypto algorithms validated successfully");
    }
    
    /**
     * Validate key pair
     */
    public boolean validateKeyPair(byte[] publicKey, byte[] privateKey, String algorithm) {
        return publicKey != null && publicKey.length > 0 &&
               privateKey != null && privateKey.length > 0 &&
               SUPPORTED_KEM_ALGORITHMS.contains(algorithm.toUpperCase());
    }
    
    /**
     * Validate symmetric key
     */
    public boolean validateSymmetricKey(byte[] key, String algorithm) {
        if (key == null || !SUPPORTED_SYMMETRIC_ALGORITHMS.contains(algorithm.toUpperCase())) {
            return false;
        }
        
        switch (algorithm.toUpperCase()) {
            case "AES-256":
            case "SALSA20":
            case "CHACHA20":
                return key.length == 32; // 256-bit key
            default:
                return false;
        }
    }
    
    /**
     * Check if algorithm is supported
     */
    public boolean isAlgorithmSupported(String algorithm, AlgorithmType type) {
        return validateAlgorithmSupport(algorithm, type);
    }
    
    /**
     * Get default algorithms
     */
    public CryptoAlgorithms getDefaultAlgorithms() {
        return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
    }
    
    // ===========================
    // ALGORITHM INFORMATION
    // ===========================
    
    /**
     * Get supported KEM algorithms
     */
    public List<String> getSupportedKEMAlgorithms() {
        return new ArrayList<>(SUPPORTED_KEM_ALGORITHMS);
    }
    
    /**
     * Get supported symmetric algorithms
     */
    public List<String> getSupportedSymmetricAlgorithms() {
        return new ArrayList<>(SUPPORTED_SYMMETRIC_ALGORITHMS);
    }
    
    /**
     * Get supported signature algorithms
     */
    public List<String> getSupportedSignatureAlgorithms() {
        return new ArrayList<>(SUPPORTED_SIGNATURE_ALGORITHMS);
    }
    
    /**
     * Get algorithm information
     */
    public AlgorithmInfo getAlgorithmInfo(String algorithm, AlgorithmType type) {
        String key = algorithm.toUpperCase() + "_" + type.name();
        return algorithmInfoCache.get(key);
    }
    
    // ===========================
    // FILE OPERATIONS
    // ===========================
    
    /**
     * Encrypt file
     */
    public EncryptedFileResult encryptFile(String filePath, byte[] symmetricKey, String algorithm) {
        checkInitialized();
        
        if (filePath == null || symmetricKey == null) {
            throw new IllegalArgumentException("File path and symmetric key cannot be null");
        }
        
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        
        try {
            Log.d(TAG, "Encrypting file: " + filePath);
            
            // Read file content
            byte[] fileContent = readFileBytes(inputFile);
            
            // Encrypt content
            byte[] encryptedContent = encryptMessage(new String(fileContent, "ISO-8859-1"), symmetricKey, algorithm);
            
            // Generate encrypted file path
            String encryptedFilePath = filePath + ".enc";
            File encryptedFile = new File(encryptedFilePath);
            
            // Write encrypted content
            writeFileBytes(encryptedFile, encryptedContent);
            
            // Generate hash
            byte[] hash = generateHash(encryptedContent).getBytes("UTF-8");
            
            Log.d(TAG, "File encrypted successfully: " + encryptedFilePath);
            return new EncryptedFileResult(encryptedFilePath, hash, encryptedFile.length());
            
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting file: " + filePath, e);
            throw new RuntimeException("Failed to encrypt file", e);
        }
    }
    
    /**
     * Decrypt file
     */
    public String decryptFile(EncryptedFileResult encryptedFile, byte[] symmetricKey) {
        checkInitialized();
        
        if (encryptedFile == null || symmetricKey == null) {
            throw new IllegalArgumentException("Encrypted file result and symmetric key cannot be null");
        }
        
        String encryptedFilePath = encryptedFile.getEncryptedFilePath();
        File inputFile = new File(encryptedFilePath);
        
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("Encrypted file does not exist: " + encryptedFilePath);
        }
        
        try {
            Log.d(TAG, "Decrypting file: " + encryptedFilePath);
            
            // Read encrypted content
            byte[] encryptedContent = readFileBytes(inputFile);
            
            // Decrypt content - detect algorithm from extension or use default
            String decryptedContent = decryptMessage(encryptedContent, symmetricKey, "AES-256");
            
            // Generate decrypted file path
            String decryptedFilePath = encryptedFilePath.replace(".enc", "");
            File decryptedFile = new File(decryptedFilePath);
            
            // Write decrypted content
            writeFileBytes(decryptedFile, decryptedContent.getBytes("ISO-8859-1"));
            
            Log.d(TAG, "File decrypted successfully: " + decryptedFilePath);
            return decryptedFilePath;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting file: " + encryptedFilePath, e);
            throw new RuntimeException("Failed to decrypt file", e);
        }
    }
    
    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    
    /**
     * Check if crypto manager is initialized
     */
    private void checkInitialized() {
        if (!isInitialized.get()) {
            throw new IllegalStateException("CryptoManager not initialized");
        }
    }
    
    /**
     * Initialize algorithm info cache
     */
    private void initializeAlgorithmInfoCache() {
        // KEM algorithms
        algorithmInfoCache.put("NTRU_ASYMMETRIC_KEM", 
            new AlgorithmInfo("NTRU", AlgorithmType.ASYMMETRIC_KEM, 4096, "NTRU lattice-based KEM", true, "NIST Level 3"));
        algorithmInfoCache.put("KYBER_ASYMMETRIC_KEM", 
            new AlgorithmInfo("KYBER", AlgorithmType.ASYMMETRIC_KEM, 768, "NIST standardized KEM", true, "NIST Level 3"));
        algorithmInfoCache.put("BIKE_ASYMMETRIC_KEM", 
            new AlgorithmInfo("BIKE", AlgorithmType.ASYMMETRIC_KEM, 256, "Code-based KEM", true, "NIST Level 1"));
        algorithmInfoCache.put("HQC_ASYMMETRIC_KEM", 
            new AlgorithmInfo("HQC", AlgorithmType.ASYMMETRIC_KEM, 256, "Code-based KEM", true, "NIST Level 3"));
        algorithmInfoCache.put("SABER_ASYMMETRIC_KEM", 
            new AlgorithmInfo("SABER", AlgorithmType.ASYMMETRIC_KEM, 256, "Lattice-based KEM", true, "NIST Level 3"));
        algorithmInfoCache.put("MCELIECE_ASYMMETRIC_KEM", 
            new AlgorithmInfo("MCELIECE", AlgorithmType.ASYMMETRIC_KEM, 6688, "Classic McEliece code-based KEM", true, "NIST Level 5"));
        algorithmInfoCache.put("FRODO_ASYMMETRIC_KEM", 
            new AlgorithmInfo("FRODO", AlgorithmType.ASYMMETRIC_KEM, 976, "Conservative lattice-based KEM", true, "NIST Level 3"));
        
        // Symmetric algorithms
        algorithmInfoCache.put("AES-256_SYMMETRIC", 
            new AlgorithmInfo("AES-256", AlgorithmType.SYMMETRIC, 256, "AES-256-GCM authenticated encryption", true, "256-bit"));
        algorithmInfoCache.put("SALSA20_SYMMETRIC", 
            new AlgorithmInfo("SALSA20", AlgorithmType.SYMMETRIC, 256, "Salsa20 stream cipher", true, "256-bit"));
        algorithmInfoCache.put("CHACHA20_SYMMETRIC", 
            new AlgorithmInfo("CHACHA20", AlgorithmType.SYMMETRIC, 256, "ChaCha20 stream cipher", true, "256-bit"));
        
        // Signature algorithms
        algorithmInfoCache.put("FALCON_DIGITAL_SIGNATURE", 
            new AlgorithmInfo("FALCON", AlgorithmType.DIGITAL_SIGNATURE, 512, "NIST standardized signature scheme", true, "NIST Level 1"));
        algorithmInfoCache.put("DILITHIUM_DIGITAL_SIGNATURE", 
            new AlgorithmInfo("DILITHIUM", AlgorithmType.DIGITAL_SIGNATURE, 3, "NIST standardized signature scheme", true, "NIST Level 3"));
        algorithmInfoCache.put("RAINBOW_DIGITAL_SIGNATURE", 
            new AlgorithmInfo("RAINBOW", AlgorithmType.DIGITAL_SIGNATURE, 3, "Multivariate signature scheme", false, "NIST Level 3"));
    }
    
    /**
     * Read file bytes
     */
    private byte[] readFileBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            return buffer;
        }
    }
    
    /**
     * Write file bytes
     */
    private void writeFileBytes(File file, byte[] data) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
    
    // ===========================
    // KEM ALGORITHM IMPLEMENTATIONS
    // ===========================
    
    private AsymmetricCipherKeyPair generateNTRUKeyPair() {
        NTRUKeyPairGenerator generator = new NTRUKeyPairGenerator();
        NTRUKeyGenerationParameters params = new NTRUKeyGenerationParameters(secureRandom, NTRUParameters.ntruhps4096821);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateKyberKeyPair() {
        KyberKeyPairGenerator generator = new KyberKeyPairGenerator();
        KyberKeyGenerationParameters params = new KyberKeyGenerationParameters(secureRandom, KyberParameters.kyber768);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateBIKEKeyPair() {
        BIKEKeyPairGenerator generator = new BIKEKeyPairGenerator();
        BIKEKeyGenerationParameters params = new BIKEKeyGenerationParameters(secureRandom, BIKEParameters.bike256);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateHQCKeyPair() {
        HQCKeyPairGenerator generator = new HQCKeyPairGenerator();
        HQCKeyGenerationParameters params = new HQCKeyGenerationParameters(secureRandom, HQCParameters.hqc256);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateSABERKeyPair() {
        SABERKeyPairGenerator generator = new SABERKeyPairGenerator();
        SABERKeyGenerationParameters params = new SABERKeyGenerationParameters(secureRandom, SABERParameters.firesaberkem256r3);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateMcElieceKeyPair() {
        CMCEKeyPairGenerator generator = new CMCEKeyPairGenerator();
        CMCEKeyGenerationParameters params = new CMCEKeyGenerationParameters(secureRandom, CMCEParameters.mceliece8192128r3);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    private AsymmetricCipherKeyPair generateFrodoKeyPair() {
        FrodoKeyPairGenerator generator = new FrodoKeyPairGenerator();
        FrodoKeyGenerationParameters params = new FrodoKeyGenerationParameters(secureRandom, FrodoParameters.frodokem976aes);
        generator.init(params);
        return generator.generateKeyPair();
    }
    
    // KEM Encapsulation methods
    private SecretWithEncapsulation encapsulateNTRU(byte[] publicKeyBytes) {
        NTRUPublicKeyParameters publicKey = new NTRUPublicKeyParameters(NTRUParameters.ntruhps4096821, publicKeyBytes);
        NTRUKEMGenerator generator = new NTRUKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateKyber(byte[] publicKeyBytes) {
        KyberPublicKeyParameters publicKey = new KyberPublicKeyParameters(KyberParameters.kyber768, publicKeyBytes);
        KyberKEMGenerator generator = new KyberKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateBIKE(byte[] publicKeyBytes) {
        BIKEPublicKeyParameters publicKey = new BIKEPublicKeyParameters(BIKEParameters.bike256, publicKeyBytes);
        BIKEKEMGenerator generator = new BIKEKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateHQC(byte[] publicKeyBytes) {
        HQCPublicKeyParameters publicKey = new HQCPublicKeyParameters(HQCParameters.hqc256, publicKeyBytes);
        HQCKEMGenerator generator = new HQCKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateSABER(byte[] publicKeyBytes) {
        SABERPublicKeyParameters publicKey = new SABERPublicKeyParameters(SABERParameters.firesaberkem256r3, publicKeyBytes);
        SABERKEMGenerator generator = new SABERKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateMcEliece(byte[] publicKeyBytes) {
        CMCEPublicKeyParameters publicKey = new CMCEPublicKeyParameters(CMCEParameters.mceliece8192128r3, publicKeyBytes);
        CMCEKEMGenerator generator = new CMCEKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    private SecretWithEncapsulation encapsulateFrodo(byte[] publicKeyBytes) {
        FrodoPublicKeyParameters publicKey = new FrodoPublicKeyParameters(FrodoParameters.frodokem976aes, publicKeyBytes);
        FrodoKEMGenerator generator = new FrodoKEMGenerator(secureRandom);
        return generator.generateEncapsulated(publicKey);
    }
    
    // KEM Extraction methods
    private byte[] extractNTRU(byte[] capsuleBytes, byte[] privateKeyBytes) {
        NTRUPrivateKeyParameters privateKey = new NTRUPrivateKeyParameters(NTRUParameters.ntruhps4096821, privateKeyBytes);
        NTRUKEMExtractor extractor = new NTRUKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractKyber(byte[] capsuleBytes, byte[] privateKeyBytes) {
        KyberPrivateKeyParameters privateKey = new KyberPrivateKeyParameters(KyberParameters.kyber768, privateKeyBytes);
        KyberKEMExtractor extractor = new KyberKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractBIKE(byte[] capsuleBytes, byte[] privateKeyBytes) {
        BIKEPrivateKeyParameters privateKey = deserializeBIKEPrivateKey(privateKeyBytes);
        BIKEKEMExtractor extractor = new BIKEKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractHQC(byte[] capsuleBytes, byte[] privateKeyBytes) {
        HQCPrivateKeyParameters privateKey = new HQCPrivateKeyParameters(HQCParameters.hqc256, privateKeyBytes);
        HQCKEMExtractor extractor = new HQCKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractSABER(byte[] capsuleBytes, byte[] privateKeyBytes) {
        SABERPrivateKeyParameters privateKey = new SABERPrivateKeyParameters(SABERParameters.firesaberkem256r3, privateKeyBytes);
        SABERKEMExtractor extractor = new SABERKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractMcEliece(byte[] capsuleBytes, byte[] privateKeyBytes) {
        CMCEPrivateKeyParameters privateKey = new CMCEPrivateKeyParameters(CMCEParameters.mceliece8192128r3, privateKeyBytes);
        CMCEKEMExtractor extractor = new CMCEKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    private byte[] extractFrodo(byte[] capsuleBytes, byte[] privateKeyBytes) {
        FrodoPrivateKeyParameters privateKey = new FrodoPrivateKeyParameters(FrodoParameters.frodokem976aes, privateKeyBytes);
        FrodoKEMExtractor extractor = new FrodoKEMExtractor(privateKey);
        return extractor.extractSecret(capsuleBytes);
    }
    
    // BIKE specific deserialization
    private BIKEPrivateKeyParameters deserializeBIKEPrivateKey(byte[] privateKeyBytes) {
        BIKEParameters params = BIKEParameters.bike256;
        int R_BYTE = params.getRByte();
        int L_BYTE = params.getLByte();
        
        byte[] h0 = new byte[R_BYTE];
        byte[] h1 = new byte[R_BYTE];
        byte[] sigma = new byte[L_BYTE];
        
        System.arraycopy(privateKeyBytes, 0, h0, 0, R_BYTE);
        System.arraycopy(privateKeyBytes, R_BYTE, h1, 0, R_BYTE);
        System.arraycopy(privateKeyBytes, R_BYTE * 2, sigma, 0, L_BYTE);
        
        return new BIKEPrivateKeyParameters(params, h0, h1, sigma);
    }
    
    // ===========================
    // SYMMETRIC ENCRYPTION IMPLEMENTATIONS
    // ===========================
    
    private byte[] encryptAES256(byte[] data, byte[] key) throws Exception {
        byte[] iv = new byte[12]; // GCM IV
        secureRandom.nextBytes(iv);
        
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), 128, iv);
        cipher.init(true, parameters);
        
        byte[] encrypted = new byte[cipher.getOutputSize(data.length)];
        int len = cipher.processBytes(data, 0, data.length, encrypted, 0);
        len += cipher.doFinal(encrypted, len);
        
        // Prepend IV to encrypted data
        byte[] result = new byte[iv.length + len];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, len);
        
        return result;
    }
    
    private byte[] decryptAES256(byte[] encryptedData, byte[] key) throws Exception {
        // Extract IV
        byte[] iv = new byte[12];
        System.arraycopy(encryptedData, 0, iv, 0, 12);
        
        // Extract encrypted data
        byte[] encrypted = new byte[encryptedData.length - 12];
        System.arraycopy(encryptedData, 12, encrypted, 0, encrypted.length);
        
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), 128, iv);
        cipher.init(false, parameters);
        
        byte[] decrypted = new byte[cipher.getOutputSize(encrypted.length)];
        int len = cipher.processBytes(encrypted, 0, encrypted.length, decrypted, 0);
        len += cipher.doFinal(decrypted, len);
        
        return Arrays.copyOf(decrypted, len);
    }
    
    private byte[] encryptSalsa20(byte[] data, byte[] key) throws Exception {
        byte[] nonce = new byte[8];
        secureRandom.nextBytes(nonce);
        
        Salsa20Engine engine = new Salsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
        
        byte[] encrypted = new byte[data.length];
        engine.processBytes(data, 0, data.length, encrypted, 0);
        
        // Prepend nonce
        byte[] result = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        
        return result;
    }
    
    private byte[] decryptSalsa20(byte[] encryptedData, byte[] key) throws Exception {
        // Extract nonce
        byte[] nonce = new byte[8];
        System.arraycopy(encryptedData, 0, nonce, 0, 8);
        
        // Extract encrypted data
        byte[] encrypted = new byte[encryptedData.length - 8];
        System.arraycopy(encryptedData, 8, encrypted, 0, encrypted.length);
        
        Salsa20Engine engine = new Salsa20Engine();
        engine.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
        
        byte[] decrypted = new byte[encrypted.length];
        engine.processBytes(encrypted, 0, encrypted.length, decrypted, 0);
        
        return decrypted;
    }
    
    private byte[] encryptChaCha20(byte[] data, byte[] key) throws Exception {
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        
        ChaCha7539Engine engine = new ChaCha7539Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
        
        byte[] encrypted = new byte[data.length];
        engine.processBytes(data, 0, data.length, encrypted, 0);
        
        // Prepend nonce
        byte[] result = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        
        return result;
    }
    
    private byte[] decryptChaCha20(byte[] encryptedData, byte[] key) throws Exception {
        // Extract nonce
        byte[] nonce = new byte[12];
        System.arraycopy(encryptedData, 0, nonce, 0, 12);
        
        // Extract encrypted data
        byte[] encrypted = new byte[encryptedData.length - 12];
        System.arraycopy(encryptedData, 12, encrypted, 0, encrypted.length);
        
        ChaCha7539Engine engine = new ChaCha7539Engine();
        engine.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
        
        byte[] decrypted = new byte[encrypted.length];
        engine.processBytes(encrypted, 0, encrypted.length, decrypted, 0);
        
        return decrypted;
    }
}