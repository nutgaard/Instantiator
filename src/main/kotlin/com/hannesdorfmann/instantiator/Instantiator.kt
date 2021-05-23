package com.hannesdorfmann.instantiator

import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmName

private typealias InstanceFactory<T> = () -> T

internal class Instantiator(config: InstantiatorConfig) {

    private val instanceFactory: MutableMap<KType, InstanceFactory<Any?>> = mutableMapOf()

    init {
        instanceFactory[Int::class.createType()] = config.intGenerator
        instanceFactory[Float::class.createType()] = config.floatGenerator
        instanceFactory[Double::class.createType()] = config.doubleGenerator
        instanceFactory[String::class.createType()] = config.stringGenerator
        instanceFactory[Char::class.createType()] = config.charGenerator
        instanceFactory[Boolean::class.createType()] = config.booleanGenerator
        instanceFactory[Long::class.createType()] = config.longGenerator
        instanceFactory[Short::class.createType()] = config.shortGenerator
        instanceFactory[Byte::class.createType()] = config.byteGenerator
    }


    private fun <T : Any> createInstance(type: KType): T {
        return fromInstanceFactoryIfAvailbaleOtherwise(type) {
            val classifier = type.classifier
            if (type.classifier != null && classifier is KClass<*>) {
                createInstance(type.classifier as KClass<T>)
            } else {
                throw RuntimeException("Could not construct instance of $type")
            }
        }
    }


    private fun <T> fromInstanceFactoryIfAvailbaleOtherwise(type: KType, alternative: () -> T): T {
        val factory: InstanceFactory<T>? = instanceFactory[type] as InstanceFactory<T>?
        return if (factory != null) {
            factory()
        } else {
            alternative()
        }
    }

    internal fun <T : Any> createInstance(clazz: KClass<T>): T {

        // Singleton objects
        val singletonObject = clazz.objectInstance
        if (singletonObject != null) {
            return singletonObject
        }

        val type = clazz.createType()
        if (clazz.isSubclassOf(Enum::class)) {
            return fromInstanceFactoryIfAvailbaleOtherwise(type) {
                val enumConstants = Class.forName(clazz.jvmName).enumConstants
                enumConstants[Random.nextInt(enumConstants.size)] as T
            }
        }

        return fromInstanceFactoryIfAvailbaleOtherwise(type) {
            // classes with empty constructor
            val primaryConstructor = clazz.primaryConstructor ?: throw UnsupportedOperationException(
                "Can not instantiate an instance of ${clazz} without a primary constructor"
            )

            val primaryConstructorParameters: Map<KParameter, Any?> =
                primaryConstructor.parameters.associate { parameter ->
                    parameter to createInstance(parameter.type)
                }

            if (primaryConstructorParameters.isEmpty()) {
                primaryConstructor.call()
            } else {
                primaryConstructor.callBy(primaryConstructorParameters)
            }
        }
    }
}

inline fun <reified T : Any> instance(config: InstantiatorConfig = InstantiatorConfig()): T = T::class.instance(config)

fun <T : Any> KClass<T>.instance(config: InstantiatorConfig = InstantiatorConfig()): T =
    Instantiator(config).createInstance(this)