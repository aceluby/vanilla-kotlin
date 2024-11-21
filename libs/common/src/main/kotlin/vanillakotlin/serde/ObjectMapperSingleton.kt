package vanillakotlin.serde

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

// Jackson's object mapper is meant to be used as a singleton
// We use it all over the place, so making it a global singleton object is useful
val mapper: ObjectMapper = ObjectMapper().initialize()

fun ObjectMapper.initialize(): ObjectMapper =
    this
        // writing textual dates is a little more verbose, but it provides a big advantage for human readability and debugging.
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        // This can be handy for human readability and consistency. It does add a small computational cost to serialization, but typically not
        // a substantial amount
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        // This the default naming strategy that should be used for most JSON payloads at Target unless there's a reason otherwise.
        // For example, one exception to this rule is GraphQL, which uses a lowerCamelCase naming convention
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        // only include non-null properties in output is a typical preferred option. It results in a smaller payload
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL) // only include non-null properties in output. usually preferred.
        // not failing on unknown properties is a good default when you may want to only deserialize a subset of a JSON body
        // for example when you only need a few properties
        // it IS important to fail on unknown properties when you're working with objects for which you expect to receive
        // the entire object. for example configuration files or other payloads that must be completely specified.
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // this is fine when you are ok with partial deserialization
        // all newer projects will set this, which sets some base minimum version feature settings
        .registerModule(Jdk8Module())
        // enables support for date objects used in java.time, which is an extremely valuable addition
        .registerModule(JavaTimeModule())
        // enable kotlin features and extensions - another standard feature for any kotlin project using jackson
        .registerKotlinModule()
