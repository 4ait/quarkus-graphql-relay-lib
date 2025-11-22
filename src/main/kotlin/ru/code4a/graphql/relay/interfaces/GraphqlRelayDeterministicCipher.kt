package ru.code4a.graphql.relay.interfaces

/**
 * Interface for encryption and decryption of GraphQL Relay data.
 * Implementations provide the cryptographic operations needed for secure
 * handling of node IDs and pagination cursors.
 */
interface GraphqlRelayDeterministicCipher {
  /**
   * Encrypts the input data .
   *
   * @param input The data to encrypt
   * @return The encrypted data
   */
  fun encrypt(input: ByteArray): ByteArray

  /**
   * Decrypts the input data.
   *
   * @param input The data to decrypt
   * @param key The decryption key
   * @return The decrypted data
   */
  fun decrypt(input: ByteArray): ByteArray
}
