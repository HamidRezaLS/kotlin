/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.JsModuleAndQualifierReference
import org.jetbrains.kotlin.backend.wasm.topLevelFunctionForNestedExternal
import org.jetbrains.kotlin.backend.wasm.utils.getJsFunAnnotation
import org.jetbrains.kotlin.backend.wasm.utils.getJsPrimitiveType
import org.jetbrains.kotlin.backend.wasm.utils.getWasmImportDescriptor
import org.jetbrains.kotlin.backend.wasm.getInstanceFunctionForExternalObject
import org.jetbrains.kotlin.backend.wasm.instanceCheckForExternalClass
import org.jetbrains.kotlin.backend.wasm.getJsClassForExternalClass
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.getJsModule
import org.jetbrains.kotlin.ir.backend.js.utils.getJsNameOrKotlinName
import org.jetbrains.kotlin.ir.backend.js.utils.getJsQualifier
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.js.common.isValidES5Identifier
import org.jetbrains.kotlin.name.Name

/**
 * Lower complex external declarations to top-level functions:
 *   - Property accessors
 *   - Member functions
 *   - Class constructors
 *   - Object declarations
 *   - Class instance checks
 */
class ComplexExternalDeclarationsToTopLevelFunctionsLowering(val context: WasmBackendContext) : FileLoweringPass {
    lateinit var currentFile: IrFile
    val addedDeclarations = mutableListOf<IrDeclaration>()

    override fun lower(irFile: IrFile) {
        currentFile = irFile
        for (declaration in irFile.declarations) {
            if (declaration.isEffectivelyExternal()) {
                processExternalDeclaration(declaration)
            }
        }
        irFile.declarations += addedDeclarations
        addedDeclarations.clear()
    }

    fun processExternalDeclaration(declaration: IrDeclaration) {
        declaration.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                error("Unknown external element ${element::class}")
            }

            override fun visitTypeParameter(declaration: IrTypeParameter) {
            }

            override fun visitValueParameter(declaration: IrValueParameter) {
            }

            override fun visitClass(declaration: IrClass) {
                declaration.acceptChildrenVoid(this)
                declaration.factory.stageController.restrictTo(declaration) {
                    lowerExternalClass(declaration)
                }
            }

            override fun visitProperty(declaration: IrProperty) {
                declaration.factory.stageController.restrictTo(declaration) {
                    processExternalProperty(declaration)
                }
            }

            override fun visitConstructor(declaration: IrConstructor) {
                declaration.factory.stageController.restrictTo(declaration) {
                    processExternalConstructor(declaration)
                }
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                declaration.factory.stageController.restrictTo(declaration) {
                    processExternalSimpleFunction(declaration)
                }
            }
        })
    }

    fun lowerExternalClass(klass: IrClass) {
        if (klass.kind == ClassKind.OBJECT)
            generateExternalObjectInstanceGetter(klass)

        if (klass.kind != ClassKind.INTERFACE) {
            generateInstanceCheckForExternalClass(klass)
            generateGetClassForExternalClass(klass)
        }
    }

    fun processExternalProperty(property: IrProperty) {
        if (property.isFakeOverride)
            return

        val propName: String =
            property.getJsNameOrKotlinName().identifier

        property.getter?.let { getter ->
            val dispatchReceiver = getter.dispatchReceiverParameter
            val jsCode =
                if (dispatchReceiver == null)
                    "() => ${referenceTopLevelExternalDeclaration(property)}"
                else
                    "(_this) => ${propName.toSavePropertyAccess("_this")}"

            val res = createExternalJsFunction(
                property.name,
                "_\$external_prop_getter",
                resultType = getter.returnType,
                jsCode = jsCode
            )

            if (dispatchReceiver != null) {
                res.addValueParameter("_this", dispatchReceiver.type)
            }

            getter.topLevelFunctionForNestedExternal = res
        }

        property.setter?.let { setter ->
            val dispatchReceiver = setter.dispatchReceiverParameter
            val jsCode =
                if (dispatchReceiver == null)
                    "(v) => ${referenceTopLevelExternalDeclaration(property)} = v"
                else
                    "(_this, v) => ${propName.toSavePropertyAccess("_this")} = v"

            val res = createExternalJsFunction(
                property.name,
                "_\$external_prop_setter",
                resultType = setter.returnType,
                jsCode = jsCode
            )

            if (dispatchReceiver != null) {
                res.addValueParameter("_this", dispatchReceiver.type)
            }
            val setterParameter = setter.parameters.first { it.kind == IrParameterKind.Regular }.type
            res.addValueParameter("v", setterParameter)

            setter.topLevelFunctionForNestedExternal = res
        }
    }

    private fun StringBuilder.appendExternalClassReference(klass: IrClass) {
        val parent = klass.parent
        if (parent is IrClass) {

            // This is hack to support Kotlin/JS like implementation of IDL string enums bindings
            // with error suppression:
            //
            //    @JsName("null")
            //    @Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
            //    public external interface CanvasFillRule {
            //        companion object
            //    }
            //    public inline val CanvasFillRule.Companion.NONZERO: CanvasFillRule get() = "nonzero".asDynamic().unsafeCast<CanvasFillRule>()
            //
            // Kotlin/JS translates access to CanvasFillRule.Companion as `null` due to @JsName("null"),
            // but Kotlin/Wasm fails to do this due to stricter null checks on interop boundary.
            //
            // Instead, as a temporary solution, we evaluate such companion object to an empty JS object.
            // TODO: Optimize (KT-60661)
            if (parent.isInterface) {
                append("({})")
                return
            }

            appendExternalClassReference(parent)
            if (klass.isCompanion) {
                // Reference to external companion object is reference to its parent class
                return
            }

            append(klass.getJsNameOrKotlinName().identifier.toSavePropertyAccess(isTopLevel = false))
        } else {
            append(referenceTopLevelExternalDeclaration(klass))
        }
    }

    private fun String.toSavePropertyAccess(
        receiver: String = "",
        isTopLevel: Boolean = receiver.isEmpty(),
    ) = StringBuilder().apply {
        append(receiver)
        if (isValidES5Identifier()) {
            if (!isTopLevel) append('.')
            append(this@toSavePropertyAccess)
        } else {
            if (isTopLevel) append("globalThis")
            append("['")
            append(replace("'", "\\'"))
            append("']")
        }

    }.toString()

    fun processExternalConstructor(constructor: IrConstructor) {
        val klass = constructor.constructedClass

        // External interfaces can have synthetic primary constructors in K/JS
        if (klass.isInterface)
            return

        processFunctionOrConstructor(
            function = constructor,
            name = klass.name,
            returnType = klass.defaultType,
            isConstructor = true,
            jsFunctionReference = buildString { appendExternalClassReference(klass) }
        )
    }

    fun processExternalSimpleFunction(function: IrSimpleFunction) {
        // Skip JS interop adapters form WasmImport.
        // It needs to keep original signature to interop with other Wasm modules.
        if (function.getWasmImportDescriptor() != null)
            return

        val jsFun = function.getJsFunAnnotation()
        // Wrap external functions without @JsFun to lambdas `foo` -> `(a, b) => foo(a, b)`.
        // This way we wouldn't fail if we don't call them.
        if (jsFun != null &&
            function.parameters.all { it.defaultValue == null && it.varargElementType == null } &&
            currentFile.getJsQualifier() == null &&
            currentFile.getJsModule() == null
        ) {
            return
        }

        if (function.isFakeOverride) {
            return
        }

        val jsFunctionReference = when {
            jsFun != null -> "($jsFun)"
            function.isTopLevelDeclaration -> referenceTopLevelExternalDeclaration(function)
            else -> function.getJsNameOrKotlinName().identifier.toSavePropertyAccess(isTopLevel = false)
        }

        processFunctionOrConstructor(
            function = function,
            name = function.name,
            returnType = function.returnType,
            isConstructor = false,
            jsFunctionReference = jsFunctionReference
        )
    }

    private val IrFunction.isSetOperator get() =
        (this is IrSimpleFunction) && isOperator && name.asString() == "set"

    private val IrFunction.isGetOperator get() =
        (this is IrSimpleFunction) && isOperator && name.asString() == "get"

    private fun createJsCodeForFunction(
        function: IrFunction,
        numDefaultParameters: Int,
        isConstructor: Boolean,
        jsFunctionReference: String
    ): String {
        val dispatchReceiver = function.dispatchReceiverParameter
        val valueParameters = function.parameters.filter { it.kind == IrParameterKind.Regular }
        val numValueParameters = valueParameters.size

        return buildString {
            append("(")
            if (dispatchReceiver != null) {
                append("_this, ")
            }
            appendParameterList(numValueParameters, isEnd = numDefaultParameters == 0)

            // Parameters with default values are handled via adding additional flags to indicate that parameter is default .
            //
            //   external fun foo(x: Int, y: Int = definedExternally, z: Int = definedExternally)
            //
            //     =>
            //
            //   @JsCode("""(x, y, z, isDefault1, isDefault2) =>
            //      foo(
            //          x,
            //          isDefault1 ? undefined : y,
            //          isDefault2 ? undefined : z
            //      )
            //   """)
            //   external fun foo(x: Int, y: Int, z: Int, isDefault1: Int, isDefault: Int)

            appendParameterList(numDefaultParameters, "isDefault", isEnd = true)
            append(") => ")
            if (isConstructor) {
                append("new ")
            }
            if (dispatchReceiver != null) {
                append("_this")
            }
            append(jsFunctionReference)
            append("(")

            val numNonDefaultParameters = numValueParameters - numDefaultParameters
            repeat(numNonDefaultParameters) {
                if (valueParameters[it].isVararg) {
                    append("...")
                }
                append("p$it")
                if (numDefaultParameters != 0 || it + 1 < numNonDefaultParameters)
                    append(", ")
            }
            repeat(numDefaultParameters) {
                if (valueParameters[numNonDefaultParameters + it].isVararg) {
                    append("...")
                } else {
                    append("isDefault$it ? undefined : ")
                }
                append("p${numNonDefaultParameters + it}, ")
            }
            append(")")
        }
    }

    fun processFunctionOrConstructor(
        function: IrFunction,
        name: Name,
        returnType: IrType,
        isConstructor: Boolean,
        jsFunctionReference: String
    ) {
        val dispatchReceiver = function.dispatchReceiverParameter
        val valueParameters = function.parameters.filter { it.kind == IrParameterKind.Regular }

        val numDefaultParameters =
            numDefaultParametersForExternalFunction(function)

        val jsCode = when {
            function.isSetOperator -> "(_this, i, value) => _this[i] = value"
            function.isGetOperator -> "(_this, i) => _this[i]"
            else -> createJsCodeForFunction(function, numDefaultParameters, isConstructor, jsFunctionReference)
        }

        val res = createExternalJsFunction(
            name,
            "_\$external_fun",
            resultType = returnType,
            jsCode = jsCode
        )
        if (dispatchReceiver != null) {
            res.addValueParameter("_this", dispatchReceiver.type)
        }
        valueParameters.forEach {
            res.addValueParameter(
                name = it.name,
                type = if (it.type.isPrimitiveType(false)) it.type else it.type.makeNullable()
            ).apply {
                varargElementType = it.varargElementType
            }
        }
        // Using Int type with 0 and 1 values to prevent overhead of converting Boolean to true and false
        repeat(numDefaultParameters) { res.addValueParameter("isDefault$it", context.irBuiltIns.intType) }
        function.topLevelFunctionForNestedExternal = res
    }

    fun generateExternalObjectInstanceGetter(obj: IrClass) {
        obj.getInstanceFunctionForExternalObject = createExternalJsFunction(
            obj.name,
            "_\$external_object_getInstance",
            resultType = obj.defaultType,
            jsCode = buildString {
                append("() => ")
                appendExternalClassReference(obj)
            }
        )
    }

    fun generateInstanceCheckForExternalClass(klass: IrClass) {
        klass.instanceCheckForExternalClass = createExternalJsFunction(
            klass.name,
            "_\$external_class_instanceof",
            resultType = context.irBuiltIns.booleanType,
            jsCode = buildString {
                val jsPrimitiveType = klass.getJsPrimitiveType()
                if (jsPrimitiveType != null) {
                    append("(x) => typeof x === '$jsPrimitiveType'")
                } else {
                    append("(x) => x instanceof ")
                    appendExternalClassReference(klass)
                }
            }
        ).also {
            it.addValueParameter("x", context.irBuiltIns.anyType)
        }
    }

    fun generateGetClassForExternalClass(klass: IrClass) {
        klass.getJsClassForExternalClass = createExternalJsFunction(
            klass.name,
            "_\$external_class_get",
            resultType = context.wasmSymbols.jsRelatedSymbols.jsAnyType.makeNullable(),
            jsCode = buildString {
                append("() => ")
                appendExternalClassReference(klass)
            }
        )
    }

    private fun createExternalJsFunction(
        originalName: Name,
        suffix: String,
        resultType: IrType,
        jsCode: String,
    ): IrSimpleFunction {
        val res = createExternalJsFunction(context, originalName, suffix, resultType, jsCode)
        res.parent = currentFile
        addedDeclarations += res
        return res
    }

    private fun referenceTopLevelExternalDeclaration(declaration: IrDeclarationWithName): String {
        var name: String? = declaration.getJsNameOrKotlinName().identifier

        val qualifier = currentFile.getJsQualifier()

        val module = currentFile.getJsModule()
            ?: declaration.getJsModule()?.also {
                name = if (declaration is IrClass && declaration.isObject) null else "default"
            }

        if (qualifier == null && module == null) {
            require(name != null) { "Unexpected null inside declaration Name identifier "}
            return name.toSavePropertyAccess()
        }

        val qualifierReference = JsModuleAndQualifierReference(module, qualifier)
        if (module != null) {
            context.getFileContext(currentFile).jsModuleAndQualifierReferences += qualifierReference
        }
        return name?.toSavePropertyAccess(qualifierReference.jsQualifierReferenceIdentifier) ?: qualifierReference.jsQualifierReferenceIdentifier
    }
}

fun createExternalJsFunction(
    context: WasmBackendContext,
    originalName: Name,
    suffix: String,
    resultType: IrType,
    jsCode: String,
): IrSimpleFunction {
    val res = context.irFactory.buildFun {
        name = Name.identifier(originalName.asStringStripSpecialMarkers() + suffix)
        returnType = resultType
        isExternal = true
    }
    val builder = context.createIrBuilder(res.symbol)
    res.annotations += builder.irCallConstructor(context.wasmSymbols.jsRelatedSymbols.jsFunConstructor, typeArguments = emptyList()).also {
        it.arguments[0] = builder.irString(jsCode)
    }
    return res
}

/**
 * Redirect usages of complex declarations to top-level functions
 */
class ComplexExternalDeclarationsUsageLowering(val context: WasmBackendContext) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.acceptVoid(declarationTransformer)
    }

    private val declarationTransformer = object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitFile(declaration: IrFile) {
            process(declaration)
        }

        override fun visitClass(declaration: IrClass) {
            if (!declaration.isExternal) {
                process(declaration)
            }
        }

        private fun process(container: IrDeclarationContainer) {
            container.declarations.transformFlat { member ->
                if (member is IrFunction && member.topLevelFunctionForNestedExternal != null) {
                    emptyList()
                } else {
                    member.acceptVoid(this)
                    null
                }
            }
        }

        override fun visitBody(body: IrBody) {
            body.transformChildrenVoid(usagesTransformer)
        }
    }

    private val usagesTransformer = object : IrElementTransformerVoid() {
        override fun visitCall(expression: IrCall): IrExpression {
            expression.transformChildrenVoid()
            return transformCall(expression)
        }

        override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
            expression.transformChildrenVoid()
            return transformCall(expression)
        }

        override fun visitGetObjectValue(expression: IrGetObjectValue): IrExpression {
            val externalGetInstance = expression.symbol.owner.getInstanceFunctionForExternalObject ?: return expression
            return IrCallImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = expression.type,
                symbol = externalGetInstance.symbol,
                typeArgumentsCount = 0
            )
        }

        fun transformCall(call: IrFunctionAccessExpression): IrExpression {
            val oldFun = call.symbol.owner.realOverrideTarget
            val newFun: IrSimpleFunction = oldFun.topLevelFunctionForNestedExternal ?: return call

            val newCall = IrCallImpl(call.startOffset, call.endOffset, newFun.returnType, newFun.symbol)

            // Copy arguments or fill with default
            for (parameter in oldFun.parameters) {
                val arg = call.arguments[parameter.indexInParameters]
                newCall.arguments[parameter.indexInParameters] = if (
                    arg == null &&
                    !parameter.isVararg // Handled with WasmVarargExpressionLowering
                ) IrConstImpl.defaultValueForType(UNDEFINED_OFFSET, UNDEFINED_OFFSET, parameter.type)
                else arg
            }

            // Add default arguments flags if needed
            val numDefaultParameters = numDefaultParametersForExternalFunction(oldFun)
            val firstOldDefaultArgumentIdx = oldFun.parameters.size - numDefaultParameters
            val firstDefaultFlagArgumentIdx = newFun.parameters.size - numDefaultParameters
            for (i in 0..<numDefaultParameters) {
                val value = if (call.arguments[i + firstOldDefaultArgumentIdx] == null) 1 else 0
                newCall.arguments[firstDefaultFlagArgumentIdx + i] =
                    IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, value)
            }

            return newCall
        }
    }
}

private fun numDefaultParametersForExternalFunction(function: IrFunction): Int {
    if (function is IrSimpleFunction) {
        // Default parameters can be in overridden external functions
        val numDefaultParametersInOverrides =
            function.overriddenSymbols.maxOfOrNull {
                numDefaultParametersForExternalFunction(it.owner)
            } ?: 0

        if (numDefaultParametersInOverrides > 0) {
            return numDefaultParametersInOverrides
        }
    }

    val firstDefaultParameterIndex = function.parameters.indexOfFirst { it.defaultValue != null }
    return if (firstDefaultParameterIndex == -1) {
        0
    } else {
        function.parameters.size - firstDefaultParameterIndex
    }
}
