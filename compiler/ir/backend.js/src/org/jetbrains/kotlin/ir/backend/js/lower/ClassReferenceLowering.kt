/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.ir.Symbols
import org.jetbrains.kotlin.backend.common.ir.createArrayOfExpression
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.utils.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.config.compileLongAsBigint
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.*

class JsClassReferenceLowering(context: JsIrBackendContext) : ClassReferenceLowering(context) {
    private val getClassData = context.intrinsics.jsClass
    private val primitiveClassesObject = context.intrinsics.primitiveClassesObject

    private val primitiveClassProperties by lazy(LazyThreadSafetyMode.NONE) {
        primitiveClassesObject.owner.declarations.filterIsInstance<IrProperty>()
    }

    private val primitiveClassFunctionClass by lazy(LazyThreadSafetyMode.NONE) {
        primitiveClassesObject.owner.declarations
            .findIsInstanceAnd<IrSimpleFunction> { it.name == Name.identifier("functionClass") }!!
    }

    private fun primitiveClassProperty(name: String) =
        primitiveClassProperties.singleOrNull { it.name == Name.identifier(name) }?.getter
            ?: primitiveClassesObject.owner.declarations
                .filterIsInstance<IrSimpleFunction>()
                .single { it.name == Name.special("<get-$name>") }

    private val finalPrimitiveClasses by lazy(LazyThreadSafetyMode.NONE) {
        mapOf(
            IrType::isBoolean to "booleanClass",
            IrType::isByte to "byteClass",
            IrType::isShort to "shortClass",
            IrType::isInt to "intClass",
            IrType::isFloat to "floatClass",
            IrType::isDouble to "doubleClass",
            { type: IrType -> type.isLong() && context.configuration.compileLongAsBigint } to "longClass",
            IrType::isArray to "arrayClass",
            IrType::isString to "stringClass",
            IrType::isBooleanArray to "booleanArrayClass",
            IrType::isCharArray to "charArrayClass",
            IrType::isByteArray to "byteArrayClass",
            IrType::isShortArray to "shortArrayClass",
            IrType::isIntArray to "intArrayClass",
            IrType::isLongArray to "longArrayClass",
            IrType::isFloatArray to "floatArrayClass",
            IrType::isDoubleArray to "doubleArrayClass"
        ).mapValues {
            primitiveClassProperty(it.value)
        }
    }

    private val openPrimitiveClasses by lazy(LazyThreadSafetyMode.NONE) {
        mapOf(
            IrType::isAny to "anyClass",
            IrType::isNumber to "numberClass",
            IrType::isThrowable to "throwableClass",
            IrType::isNothing to "nothingClass"
        ).mapValues {
            primitiveClassProperty(it.value)
        }
    }

    private fun getPrimitiveClass(target: IrSimpleFunction, returnType: IrType) =
        JsIrBuilder.buildCall(target.symbol, returnType).apply {
            arguments[0] = JsIrBuilder.buildGetObjectValue(
                type = primitiveClassesObject.defaultType,
                classSymbol = primitiveClassesObject
            )
        }

    override fun getFinalPrimitiveKClass(returnType: IrType, typeArgument: IrType): IrCall? {
        for ((typePredicate, v) in finalPrimitiveClasses) {
            if (typePredicate(typeArgument))
                return getPrimitiveClass(v, returnType)
        }

        return null
    }


    override fun getOpenPrimitiveKClass(returnType: IrType, typeArgument: IrType): IrCall? {
        for ((typePredicate, v) in openPrimitiveClasses) {
            if (typePredicate(typeArgument))
                return getPrimitiveClass(v, returnType)
        }

        if (typeArgument.isFunction()) {
            val functionInterface = typeArgument.getClass()!!
            val arity = functionInterface.typeParameters.size - 1
            return getPrimitiveClass(primitiveClassFunctionClass, returnType).apply {
                arguments[1] = JsIrBuilder.buildInt(context.irBuiltIns.intType, arity)
            }
        }

        return null
    }

    override fun callGetKClass(
        returnType: IrType,
        typeArgument: IrType
    ): IrCall {
        val primitiveKClass =
            getFinalPrimitiveKClass(returnType, typeArgument) ?: getOpenPrimitiveKClass(returnType, typeArgument)

        if (primitiveKClass != null)
            return primitiveKClass

        return JsIrBuilder.buildCall(reflectionSymbols.getKClass, returnType, listOf(typeArgument))
            .apply {
                arguments[0] = callGetClassByType(typeArgument)
            }
    }

    private fun callGetClassByType(type: IrType) =
        JsIrBuilder.buildCall(
            getClassData,
            typeArguments = listOf(type),
            origin = JsStatementOrigins.CLASS_REFERENCE
        )
}

abstract class ClassReferenceLowering(val context: JsCommonBackendContext) : BodyLoweringPass {

    protected val reflectionSymbols get() = context.reflectionSymbols

    protected open fun getFinalPrimitiveKClass(returnType: IrType, typeArgument: IrType): IrCall? = null
    protected open fun getOpenPrimitiveKClass(returnType: IrType, typeArgument: IrType): IrCall? = null

    private fun callGetKClassFromExpression(returnType: IrType, typeArgument: IrType, argument: IrExpression): IrExpression {
        val primitiveKClass = getFinalPrimitiveKClass(returnType, typeArgument)
        if (primitiveKClass != null)
            return JsIrBuilder.buildBlock(returnType, listOf(argument, primitiveKClass))

        return JsIrBuilder.buildCall(reflectionSymbols.getKClassFromExpression, returnType, listOf(typeArgument)).apply {
            arguments[0] = argument
        }
    }

    abstract fun callGetKClass(
        returnType: IrType = reflectionSymbols.getKClass.owner.returnType,
        typeArgument: IrType
    ): IrCall

    private fun buildCall(name: IrSimpleFunctionSymbol, vararg args: IrExpression): IrExpression =
        JsIrBuilder.buildCall(name).apply {
            args.forEachIndexed { index, irExpression ->
                arguments[index] = irExpression
            }
        }

    private fun createKType(type: IrType, visitedTypeParams: MutableSet<IrTypeParameter>): IrExpression {
        if (type is IrSimpleType)
            return createSimpleKType(type, visitedTypeParams)
        if (type is IrDynamicType)
            return createDynamicType()
        compilationException("Unexpected type", type)
    }

    private fun createDynamicType(): IrExpression {
        return buildCall(reflectionSymbols.createDynamicKType)
    }

    private fun createSimpleKType(type: IrSimpleType, visitedTypeParams: MutableSet<IrTypeParameter>): IrExpression {
        val classifier: IrClassifierSymbol = type.classifier

        // TODO: Check why do we have un-substituted reified parameters
        // if (classifier is IrTypeParameterSymbol && classifier.owner.isReified) {
        //     error("Fail")
        // }

        val kClassifier = createKClassifier(classifier, visitedTypeParams)
        val arguments = context.createArrayOfExpression(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            arrayElementType = context.reflectionSymbols.kTypeClass.defaultType,
            arrayElements = type.arguments.memoryOptimizedMap { createKTypeProjection(it, visitedTypeParams) }
        )

        val isMarkedNullable = JsIrBuilder.buildBoolean(context.irBuiltIns.booleanType, type.isMarkedNullable())
        return buildCall(
            reflectionSymbols.createKType,
            kClassifier,
            arguments,
            isMarkedNullable
        )
    }

    private fun createKTypeProjection(tp: IrTypeArgument, visitedTypeParams: MutableSet<IrTypeParameter>): IrExpression {
        if (tp !is IrTypeProjection) {
            return buildCall(reflectionSymbols.getStarKTypeProjection)
        }

        val factoryName = when (tp.variance) {
            Variance.INVARIANT -> reflectionSymbols.createInvariantKTypeProjection
            Variance.IN_VARIANCE -> reflectionSymbols.createContravariantKTypeProjection
            Variance.OUT_VARIANCE -> reflectionSymbols.createCovariantKTypeProjection
        }

        val kType = createKType(tp.type, visitedTypeParams)
        return buildCall(factoryName, kType)

    }

    private fun createKClassifier(classifier: IrClassifierSymbol, visitedTypeParams: MutableSet<IrTypeParameter>): IrExpression =
        when (classifier) {
            is IrTypeParameterSymbol -> createKTypeParameter(classifier.owner, visitedTypeParams)
            else -> callGetKClass(typeArgument = classifier.defaultType)
        }

    private fun createKTypeParameter(typeParameter: IrTypeParameter, visitedTypeParams: MutableSet<IrTypeParameter>): IrExpression {
        // See KT-40173
        if (typeParameter in visitedTypeParams) TODO("Non-reified type parameters with recursive bounds are not supported yet")

        visitedTypeParams.add(typeParameter)

        val name = JsIrBuilder.buildString(context.irBuiltIns.stringType, typeParameter.name.asString())

        val upperBounds = context.createArrayOfExpression(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            arrayElementType = context.reflectionSymbols.kTypeClass.defaultType,
            arrayElements = typeParameter.superTypes.memoryOptimizedMap { createKType(it, visitedTypeParams) }
        )

        val variance = when (typeParameter.variance) {
            Variance.INVARIANT -> JsIrBuilder.buildString(context.irBuiltIns.stringType, "invariant")
            Variance.IN_VARIANCE -> JsIrBuilder.buildString(context.irBuiltIns.stringType, "in")
            Variance.OUT_VARIANCE -> JsIrBuilder.buildString(context.irBuiltIns.stringType, "out")
        }

        return buildCall(
            reflectionSymbols.createKTypeParameter,
            name,
            upperBounds,
            variance,
            typeParameter.isReified.toIrConst(context.irBuiltIns.booleanType),
            typeParameter.parent.kotlinFqName.asString().toIrConst(context.irBuiltIns.stringType),
        ).also {
            visitedTypeParams.remove(typeParameter)
        }
    }

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitGetClass(expression: IrGetClass) =
                callGetKClassFromExpression(
                    returnType = expression.type,
                    typeArgument = expression.argument.type,
                    argument = expression.argument.transform(this, null)
                )

            override fun visitClassReference(expression: IrClassReference) =
                callGetKClass(
                    returnType = expression.type,
                    typeArgument = expression.classType.makeNotNull()
                )

            override fun visitCall(expression: IrCall): IrExpression =
                if (Symbols.isTypeOfIntrinsic(expression.symbol)) {
                    createKType(expression.typeArguments[0]!!, hashSetOf())
                } else {
                    super.visitCall(expression)
                }
        })
    }
}
