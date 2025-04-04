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
 * Service for encrypting and decrypting node IDs.
 * Provides secure handling of node identifiers to prevent exposure of internal database IDs.
 *
 * @property cipher The encryption/decryption implementation
 */
@ApplicationScoped
class NodeIdCipherGraphqlNodeService(
  private val cipher: GraphqlRelayCipher
) {
  @ConfigProperty(name = "ru.code4a.graphql.relay.node.id.cipher.key-32-bytes-base64")
  private lateinit var secretKeyBase64: String

  @ConfigProperty(name = "ru.code4a.graphql.relay.node.id.cipher.salt-base64")
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
   * Encrypts a node ID container object into an opaque string ID.
   *
   * @param nodeIdContainer The container object with node type and entity IDs
   * @return Encrypted string representation of the node ID
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun encryptToNodeId(nodeIdContainer: Any): String {
    val nodeIdData = ProtoBuf.encodeToByteArray(serializer(nodeIdContainer::class.java), nodeIdContainer)

    val iv = generateIV(nodeIdData, saltBytes)

    val encrypted =
      cipher.encrypt(
        key = secretKeyBytes,
        input = nodeIdData,
        iv = iv,
      )

    return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
  }

  /**
   * Decrypts a node ID string into a typed object.
   *
   * @param clazz The expected class of the decrypted object
   * @param cursor The encrypted node ID string
   * @return Decrypted object of type T
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun <T> decryptFromNodeId(clazz: Class<T>, cursor: String): T {
    val nodeIdBytes = Base64.getUrlDecoder().decode(cursor)

    val decryptedCursor =
      cipher.decrypt(
        key = secretKeyBytes,
        input = nodeIdBytes
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
