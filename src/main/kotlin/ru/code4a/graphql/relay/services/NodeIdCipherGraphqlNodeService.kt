package ru.code4a.graphql.relay.services

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.code4a.graphql.relay.interfaces.GraphqlRelayDeterministicCipher
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
  private val cipher: GraphqlRelayDeterministicCipher
) {
  /**
   * Encrypts a node ID container object into an opaque string ID.
   *
   * @param nodeIdContainer The container object with node type and entity IDs
   * @return Encrypted string representation of the node ID
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun encryptToNodeId(nodeIdContainer: Any): String {
    val nodeIdData = ProtoBuf.encodeToByteArray(serializer(nodeIdContainer::class.java), nodeIdContainer)

    val encrypted = cipher.encrypt(nodeIdData)

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

    val decryptedCursor = cipher.decrypt(nodeIdBytes)

    return ProtoBuf.decodeFromByteArray(serializer(clazz), decryptedCursor) as T
  }
}
