package ru.code4a.graphql.relay.builds

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem
import org.jboss.jandex.DotName
import ru.code4a.graphql.relay.schema.objects.GraphqlNode

/**
 * Processor that runs during the Quarkus build-time to collect all classes that implement
 * the GraphqlNode interface and generate resources for them.
 * This enables the identification and registration of all Relay node types in the application.
 */
class NodeClassesProcessor {
  /**
   * Build step that identifies all GraphqlNode implementations and produces a resource
   * containing their fully qualified class names.
   *
   * @param combinedIndex Combined index of all application classes
   * @param resourceProducer Producer for generating build-time resources
   */
  @BuildStep
  fun produceClassesGraphqlNodeObjects(
    combinedIndex: CombinedIndexBuildItem,
    resourceProducer: BuildProducer<GeneratedResourceBuildItem>
  ) {
    val classesInstances =
      combinedIndex
        .index
        .getAllKnownImplementors(DotName.createSimple(GraphqlNode::class.java))

    val classes =
      classesInstances.map { classInstance ->
        classInstance.name()
      }
        .toSet()

    resourceProducer.produce(
      GeneratedResourceBuildItem(
        "ru/code4a/graphql/relay/gen/relaynodeobjects",
        classes.joinToString("\n").toByteArray()
      )
    )
  }
}
