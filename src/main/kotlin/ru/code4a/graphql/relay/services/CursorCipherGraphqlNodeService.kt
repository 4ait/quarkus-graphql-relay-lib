package ru.code4a.graphql.relay.services

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.code4a.graphql.relay.interfaces.GraphqlRelayCipher
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * Service for encrypting and decrypting connection cursors.
 * Provides secure handling of pagination cursors to protect internal query state.
 *
 * @property cipher The encryption/decryption implementation
 */
@ApplicationScoped
class CursorCipherGraphqlNodeService(
  private val cipher: GraphqlRelayCipher
) {
  @ConfigProperty(name = "ru.code4a.graphql.relay.cursor.cipher.key-32-bytes-base64")
  private lateinit var secretKeyBase64: String

  @ConfigProperty(name = "ru.code4a.graphql.relay.cursor.cipher.salt-base64")
  private lateinit var saltBase64: String

  private val secretKeyBytes by lazy {
    val secretKeyBase64 = Base64.getDecoder().decode(secretKeyBase64)

    if (secretKeyBase64.size != 32) {
      throw IllegalArgumentException("Secret Key must be 32 bytes")
    }

    secretKeyBase64
  }

  private val saltBytes by lazy {
    Base64.getDecoder().decode(saltBase64)
  }

  /**
   * Encrypts a cursor container object into an opaque string cursor.
   *
   * @param cursorContainer The container object with cursor information
   * @return Encrypted string representation of the cursor
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun encryptToCursor(cursorContainer: Any): String {
    val cursorData = ProtoBuf.encodeToByteArray(serializer(cursorContainer::class.java), cursorContainer)

    val iv = generateIV(cursorData, saltBytes)

    val encrypted =
      cipher.encrypt(
        key = secretKeyBytes,
        input = cursorData,
        iv = iv,
      )

    return Base64.getEncoder().encodeToString(encrypted)
  }

  /**
   * Decrypts a cursor string into a typed object.
   *
   * @param clazz The expected class of the decrypted object
   * @param cursor The encrypted cursor string
   * @return Decrypted object of type T
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun <T> decryptFromCursor(clazz: Class<T>, cursor: String): T {
    val cursorBytes = Base64.getDecoder().decode(cursor)

    val decryptedCursor =
      cipher.decrypt(
        key = secretKeyBytes,
        input = cursorBytes
      )

    return ProtoBuf.decodeFromByteArray(serializer(clazz), decryptedCursor) as T
  }

  /**
   * Generates an initialization vector (IV) for encryption based on cursor data and salt.
   * Ensures consistent encryption/decryption while preventing certain cryptographic attacks.
   *
   * @param cursorData The data to use for IV generation
   * @param salt Salt value to prevent rainbow table attacks
   * @return Byte array containing the generated IV
   */
  fun generateIV(cursorData: ByteArray, salt: ByteArray): ByteArray {
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      val dataWithSalt = salt + cursorData
      val hash = digest.digest(dataWithSalt)
      return hash.copyOf(96 / 8)
    } catch (e: NoSuchAlgorithmException) {
      throw RuntimeException("Cannot generate IV", e)
    }
  }
}
