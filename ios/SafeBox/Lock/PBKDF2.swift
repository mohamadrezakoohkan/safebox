import Foundation
import CommonCrypto

enum PBKDF2 {
    /// PBKDF2-HMAC-SHA256. System CommonCrypto — no third-party dependency.
    static func derive(password: Data, salt: Data, iterations: Int, keyLength: Int = 32) -> Data {
        var derived = Data(count: keyLength)
        let result = derived.withUnsafeMutableBytes { derivedBytes in
            password.withUnsafeBytes { passwordBytes in
                salt.withUnsafeBytes { saltBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        password.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }
        precondition(result == kCCSuccess)
        return derived
    }

    /// Constant-time XOR-accumulate comparison. Data's == is not constant-time
    /// and must not be used for hash comparison.
    static func constantTimeEquals(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var acc: UInt8 = 0
        for i in 0..<a.count {
            acc |= a[a.startIndex + i] ^ b[b.startIndex + i]
        }
        return acc == 0
    }

    static func randomSalt(byteCount: Int = 16) -> Data {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        precondition(status == errSecSuccess)
        return Data(bytes)
    }
}
