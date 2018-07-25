package de.zalando.zally.rule.zalando

import com.typesafe.config.Config
import de.zalando.zally.rule.Context
import de.zalando.zally.rule.api.Check
import de.zalando.zally.rule.api.Rule
import de.zalando.zally.rule.api.Severity
import de.zalando.zally.rule.api.Violation
import de.zalando.zally.util.getAllSchemas
import io.swagger.v3.oas.models.media.Schema
import org.springframework.beans.factory.annotation.Autowired

@Rule(
    ruleSet = ZalandoRuleSet::class,
    id = "174",
    severity = Severity.MUST,
    title = "Use common field names"
)
class CommonFieldTypesRule(@Autowired rulesConfig: Config) {

    @Suppress("UNCHECKED_CAST")
    private val commonFields = rulesConfig.getConfig("${javaClass.simpleName}.common_types").entrySet()
        .map { (key, config) -> key to config.unwrapped() as List<String?> }.toMap()

    @Check(severity = Severity.MUST)
    fun checkTypesOfCommonFields(context: Context): List<Violation> =
        context.api.getAllSchemas()
            .flatMap {
                checkAllPropertiesOf(it.schema, check = { name, schema ->
                    val violationDesc = checkField(name, schema)
                    if (violationDesc != null) {
                        context.violations(violationDesc, schema)
                    } else {
                        emptyList()
                    }
                })
            }

    private fun checkAllPropertiesOf(
        objectSchema: Schema<*>,
        check: (name: String, schema: Schema<Any>) -> Collection<Violation>
    ): Collection<Violation> {

        fun traverse(oSchema: Schema<*>): List<Violation?> = oSchema.properties.orEmpty().flatMap { (name, schema) ->
            if (schema.properties == null || schema.properties.isEmpty()) {
                check(name, schema)
            } else {
                traverse(schema)
            }
        }

        return traverse(objectSchema).filterNotNull()
    }

    internal fun checkField(name: String, property: Schema<Any>): String? =
        commonFields[name]?.let { (type, format) ->
            if (property.type != type)
                "field '$name' has type '${property.type}' (expected type '$type')"
            else if (property.format != format && format != null)
                "field '$name' has type '${property.type}' with format '${property.format}' (expected format '$format')"
            else null
        }

}
