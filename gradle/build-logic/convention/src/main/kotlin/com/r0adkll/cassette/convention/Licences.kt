package com.r0adkll.cassette.convention

import javax.inject.Inject
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Lets AboutLibraries read POMs on Kotlin/Native. It looks each library's POM up through detached
 * configurations that request no attributes, and a multiplatform module's variants (one per target,
 * plus sources) tie, so every lookup fails and the export comes out empty. These rules break that
 * tie towards the common metadata variant, which is the root module, whose POM names the library
 * and its licence. They only pick between values nobody asked for, so builds that request
 * attributes resolve as before.
 */
fun Project.resolveRootModulesWhenUnrequested() {
  dependencies.attributesSchema.apply {
    prefer(Category.CATEGORY_ATTRIBUTE, Category.LIBRARY)
    prefer(KotlinPlatformType.attribute, KotlinPlatformType.common.name)
    prefer(Usage.USAGE_ATTRIBUTE, "kotlin-metadata")
  }
}

private fun <T : Any> org.gradle.api.attributes.AttributesSchema.prefer(
  attribute: Attribute<T>,
  value: String,
) {
  attribute(attribute) {
    @Suppress("UNCHECKED_CAST")
    disambiguationRules.add(
      PreferWhenUnrequested::class.java as Class<AttributeDisambiguationRule<T>>
    ) {
      params(value)
    }
  }
}

/** Picks the candidate named [preferred] when the consumer didn't ask for a value. */
internal abstract class PreferWhenUnrequested @Inject constructor(private val preferred: String) :
  AttributeDisambiguationRule<Any> {
  override fun execute(details: MultipleCandidatesDetails<Any>) {
    if (details.consumerValue != null) return
    details.candidateValues
      .firstOrNull { ((it as? Named)?.name ?: it.toString()) == preferred }
      ?.let(details::closestMatch)
  }
}
