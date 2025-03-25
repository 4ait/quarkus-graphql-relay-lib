package ru.code4a.graphql.relay.interfaces

/**
 * Interface for encryption and decryption of GraphQL Relay data.
 * Implementations provide the cryptographic operations needed for secure
 * handling of node IDs and pagination cursors.
 */
interface GraphqlRelayCipher {
  /**
   * Encrypts the input data using the provided key and initialization vector.
   *
   * @param input The data to encrypt
   * @param key The encryption key
   * @param iv The initialization vector
   * @return The encrypted data
   */
  fun encrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

  /**
   * Decrypts the input data using the provided key.
   * The IV should be extracted from the input data by the implementation.
   *
   * @param input The data to decrypt
   * @param key The decryption key
   * @return The decrypted data
   */
  fun decrypt(input: ByteArray, key: ByteArray): ByteArray
}
