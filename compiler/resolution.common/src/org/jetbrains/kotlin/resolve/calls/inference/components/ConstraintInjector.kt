/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.builtins.functions.AllowedToUsedOnlyInK1
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.resolve.calls.inference.ForkPointBranchDescription
import org.jetbrains.kotlin.resolve.calls.inference.ForkPointData
import org.jetbrains.kotlin.resolve.calls.inference.extractAllContainingTypeVariables
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind.*
import org.jetbrains.kotlin.types.AbstractTypeApproximator
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.TypeApproximatorConfiguration
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import kotlin.math.max

class ConstraintInjector(
    val constraintIncorporator: ConstraintIncorporator,
    val typeApproximator: AbstractTypeApproximator,
    private val languageVersionSettings: LanguageVersionSettings,
    inferenceLoggerParameter: InferenceLogger? = null,
) {
    /**
     * A workaround for K1's DI: the dummy instance must be provided, but
     * because it's useless, it's better to avoid calling its members to
     * prevent performance penalties.
     */
    @OptIn(AllowedToUsedOnlyInK1::class)
    val inferenceLogger = inferenceLoggerParameter.takeIf { it !is InferenceLogger.Dummy }

    private val ALLOWED_DEPTH_DELTA_FOR_INCORPORATION = 1

    interface Context : TypeSystemInferenceExtensionContext, ConstraintSystemMarker {
        val allTypeVariables: Map<TypeConstructorMarker, TypeVariableMarker>

        var maxTypeDepthFromInitialConstraints: Int
        val notFixedTypeVariables: MutableMap<TypeConstructorMarker, MutableVariableWithConstraints>
        val fixedTypeVariables: MutableMap<TypeConstructorMarker, KotlinTypeMarker>
        val constraintsFromAllForkPoints: MutableList<Pair<IncorporationConstraintPosition, ForkPointData>>

        /**
         * @see org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage.typeVariableDependencies
         */
        val typeVariableDependencies: Map<TypeConstructorMarker, Set<TypeConstructorMarker>>

        val atCompletionState: Boolean

        fun addInitialConstraint(initialConstraint: InitialConstraint)
        fun addError(error: ConstraintSystemError)

        fun resolveForkPointsConstraints()

        fun onNewConstraintOrForkPoint()

        fun recordTypeVariableReferenceInConstraint(
            constraintOwner: TypeConstructorMarker,
            referencedVariable: TypeConstructorMarker,
        )
    }

    context(c: Context)
    fun addInitialSubtypeConstraint(
        lowerType: KotlinTypeMarker,
        upperType: KotlinTypeMarker,
        position: ConstraintPosition,
    ) {
        val initialConstraint = InitialConstraint(lowerType, upperType, UPPER, position).also { c.addInitialConstraint(it) }
        inferenceLogger?.logInitial(initialConstraint, c)

        updateAllowedTypeDepth(lowerType)
        updateAllowedTypeDepth(upperType)

        inferenceLogger.withOrigin(initialConstraint) {
            with(TypeCheckerStateForConstraintInjector(c, IncorporationConstraintPosition(initialConstraint))) {
                addSubTypeConstraintAndIncorporateIt(lowerType, upperType)
            }
        }
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun addInitialEqualityConstraintThroughSubtyping(a: KotlinTypeMarker, b: KotlinTypeMarker) {
        updateAllowedTypeDepth(a)
        updateAllowedTypeDepth(b)
        addSubTypeConstraintAndIncorporateIt(a, b)
        addSubTypeConstraintAndIncorporateIt(b, a)
    }

    context(c: Context)
    fun addInitialEqualityConstraint(a: KotlinTypeMarker, b: KotlinTypeMarker, position: ConstraintPosition) {
        val (typeVariable, equalType) = when {
            a.typeConstructor(c) is TypeVariableTypeConstructorMarker -> a to b
            b.typeConstructor(c) is TypeVariableTypeConstructorMarker -> b to a
            else -> return
        }
        val initialConstraint = InitialConstraint(typeVariable, equalType, EQUALITY, position).also { c.addInitialConstraint(it) }
        inferenceLogger?.logInitial(initialConstraint, c)

        with(TypeCheckerStateForConstraintInjector(c, IncorporationConstraintPosition(initialConstraint))) {
            // We add constraints like `T? == Foo!` in the old way
            if (!typeVariable.isRigidType() || typeVariable.isMarkedNullable()) {
                inferenceLogger.withOrigin(initialConstraint) {
                    addInitialEqualityConstraintThroughSubtyping(typeVariable, equalType)
                }
                return
            }

            updateAllowedTypeDepth(equalType)
            inferenceLogger.withOrigin(initialConstraint) {
                addEqualityConstraintAndIncorporateIt(typeVariable, equalType)
            }
        }
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun addSubTypeConstraintAndIncorporateIt(lowerType: KotlinTypeMarker, upperType: KotlinTypeMarker) {
        typeCheckerState.setConstrainingTypesToPrintDebugInfo(lowerType, upperType)
        typeCheckerState.runIsSubtypeOf(lowerType, upperType)

        processConstraints()
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun addEqualityConstraintAndIncorporateIt(typeVariable: KotlinTypeMarker, equalType: KotlinTypeMarker) {
        typeCheckerState.setConstrainingTypesToPrintDebugInfo(typeVariable, equalType)
        typeCheckerState.addEqualityConstraint(typeVariable.typeConstructor(c), equalType)

        processConstraints()
    }

    context(c: Context)
    fun processGivenForkPointBranchConstraints(
        constraintSet: Collection<Pair<TypeVariableMarker, Constraint>>,
        position: IncorporationConstraintPosition,
    ) {
        with(TypeCheckerStateForConstraintInjector(c, position)) {
            processGivenConstraints(constraintSet)
            if (languageVersionSettings.supportsFeature(LanguageFeature.InferenceEnhancementsIn21)) {
                processConstraintsIgnoringForksData()
            }
        }
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun processConstraints() {
        processConstraintsIgnoringForksData()
        typeCheckerState.extractForkPointsData()?.let { allForkPointsData ->
            allForkPointsData.mapTo(c.constraintsFromAllForkPoints) { forkPointData ->
                typeCheckerState.position to forkPointData
            }

            c.onNewConstraintOrForkPoint()

            // During completion, we start processing fork constrains immediately
            if (c.atCompletionState) {
                c.resolveForkPointsConstraints()
            }
        }
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun processConstraintsIgnoringForksData() {
        while (typeCheckerState.hasConstraintsToProcess()) {
            processGivenConstraints(typeCheckerState.extractAllConstraints()!!)
        }
    }

    context(c: Context, typeCheckerState: TypeCheckerStateForConstraintInjector)
    private fun processGivenConstraints(constraintsToProcess: Collection<Pair<TypeVariableMarker, Constraint>>) {
        for ((typeVariable, constraint) in constraintsToProcess) {
            if (shouldWeSkipConstraint(typeVariable, constraint)) continue

            val typeVariableConstructor = typeVariable.freshTypeConstructor(c)
            val constraints =
                c.notFixedTypeVariables[typeVariableConstructor] ?: typeCheckerState.fixedTypeVariable(typeVariable)

            // it is important, that we add constraint here(not inside TypeCheckerContext), because inside incorporation we read constraints
            val (addedOrNonRedundantExistedConstraint, wasAdded) = constraints.addConstraint(constraint, inferenceLogger)
            val positionFrom = constraint.position.from
            val constraintToIncorporate = when {
                wasAdded && !constraint.isNullabilityConstraint -> addedOrNonRedundantExistedConstraint
                positionFrom is FixVariableConstraintPosition<*> && positionFrom.variable == typeVariable && constraint.kind == EQUALITY ->
                    addedOrNonRedundantExistedConstraint
                else -> null
            }

            if (wasAdded) {
                c.onNewConstraintOrForkPoint()
                recordReferencesOfOtherTypeVariableInConstraint(constraint, typeVariableConstructor)
            }

            if (constraintToIncorporate != null) {
                constraintIncorporator.incorporate(typeVariable, constraintToIncorporate)
            }
        }
    }

    context(c: Context)
    private fun recordReferencesOfOtherTypeVariableInConstraint(
        constraint: Constraint,
        constraintOwnerTypeVariableConstructor: TypeConstructorMarker,
    ) {
        for (referencedTypeVariableConstructor in constraint.type.extractAllContainingTypeVariables()) {
            c.recordTypeVariableReferenceInConstraint(constraintOwnerTypeVariableConstructor, referencedTypeVariableConstructor)
        }
    }

    context(c: Context)
    private fun updateAllowedTypeDepth(type: KotlinTypeMarker) {
        c.maxTypeDepthFromInitialConstraints = max(c.maxTypeDepthFromInitialConstraints, type.typeDepth())
    }

    context(c: Context)
    private fun shouldWeSkipConstraint(typeVariable: TypeVariableMarker, constraint: Constraint): Boolean {
        if (constraint.kind == EQUALITY)
            return false

        val constraintType = constraint.type

        if (constraintType.typeConstructor() == typeVariable.freshTypeConstructor()) {
            if (constraintType.lowerBoundIfFlexible().isMarkedNullable() && constraint.kind == LOWER) return false // T? <: T

            return true // T <: T(?!)
        }

        if (constraint.position.from is DeclaredUpperBoundConstraintPosition<*> &&
            constraint.kind == UPPER && constraintType.isNullableAny()
        ) {
            return true // T <: Any?
        }

        return false
    }

    context(c: Context)
    private fun KotlinTypeMarker.isAllowedType(): Boolean {
        return typeDepth() <= c.maxTypeDepthFromInitialConstraints + ALLOWED_DEPTH_DELTA_FOR_INCORPORATION
    }

    private inner class TypeCheckerStateForConstraintInjector(
        baseState: TypeCheckerState,
        val c: Context,
        val position: IncorporationConstraintPosition
    ) : TypeCheckerStateForConstraintSystem(
        c,
        baseState.kotlinTypePreparator,
        baseState.kotlinTypeRefiner
    ), ConstraintIncorporator.Context, TypeSystemInferenceExtensionContext by c {
        constructor(c: Context, position: IncorporationConstraintPosition) : this(
            c.newTypeCheckerState(errorTypesEqualToAnything = true, stubTypesEqualToAnything = true),
            c,
            position
        )

        // We use `var` intentionally to avoid extra allocations as this property is quite "hot"
        private var possibleNewConstraints: MutableList<Pair<TypeVariableMarker, Constraint>>? = null

        private var forkPointsData: MutableList<ForkPointData>? = null
        private var stackForConstraintsSetsFromCurrentForkPoint: Stack<MutableList<ForkPointBranchDescription>>? = null
        private var stackForConstraintSetFromCurrentForkPointBranch: Stack<MutableList<Pair<TypeVariableMarker, Constraint>>>? = null

        override val languageVersionSettings: LanguageVersionSettings
            get() = this@ConstraintInjector.languageVersionSettings

        private val allowForking: Boolean
            get() = constraintIncorporator.utilContext.isForcedAllowForkingInferenceSystem

        private var baseLowerType = position.initialConstraint.a
        private var baseUpperType = position.initialConstraint.b

        private var isIncorporatingConstraintFromDeclaredUpperBound = false
        private var isIncorporatingConstraintFromNoInfer = false
        private var currentDerivedFromSet: Set<TypeVariableMarker> = emptySet()

        fun extractAllConstraints() = possibleNewConstraints.also { possibleNewConstraints = null }
        fun extractForkPointsData() = forkPointsData.also { forkPointsData = null }

        fun addPossibleNewConstraint(variable: TypeVariableMarker, constraint: Constraint) {
            val constraintsSetsFromCurrentFork = stackForConstraintsSetsFromCurrentForkPoint?.lastOrNull()
            if (constraintsSetsFromCurrentFork != null) {
                val currentConstraintSetForForkPointBranch = stackForConstraintSetFromCurrentForkPointBranch?.lastOrNull()
                require(currentConstraintSetForForkPointBranch != null) { "Constraint has been added not under fork {...} call " }
                currentConstraintSetForForkPointBranch.add(variable to constraint)
                return
            }

            if (possibleNewConstraints == null) {
                possibleNewConstraints = SmartList()
            }
            possibleNewConstraints!!.add(variable to constraint)
        }

        override fun runForkingPoint(block: ForkPointContext.() -> Unit): Boolean {
            if (!allowForking) {
                return super.runForkingPoint(block)
            }

            if (stackForConstraintsSetsFromCurrentForkPoint == null) {
                stackForConstraintsSetsFromCurrentForkPoint = SmartList()
            }

            stackForConstraintsSetsFromCurrentForkPoint!!.add(SmartList())
            val isThereSuccessfulFork = with(MyForkCreationContext()) {
                block()
                anyForkSuccessful
            }

            val constraintSets = stackForConstraintsSetsFromCurrentForkPoint?.popLast()

            when {
                // Just an optimization
                constraintSets.isNullOrEmpty() -> return isThereSuccessfulFork
                constraintSets.size > 1 -> {
                    if (forkPointsData == null) {
                        forkPointsData = SmartList()
                    }
                    forkPointsData!!.addIfNotNull(
                        constraintSets
                    )
                    return if (languageVersionSettings.supportsFeature(LanguageFeature.ForkIsNotSuccessfulWhenNoBranchIsSuccessful))
                        isThereSuccessfulFork
                    else
                        true
                }
                else -> {
                    // The emptiness case has been already handled above
                    with(c) {
                        processGivenForkPointBranchConstraints(
                            constraintSets.single(),
                            position,
                        )
                    }
                }
            }

            return isThereSuccessfulFork
        }

        private inner class MyForkCreationContext : ForkPointContext {
            var anyForkSuccessful = false

            override fun fork(block: () -> Boolean) {
                if (stackForConstraintSetFromCurrentForkPointBranch == null) {
                    stackForConstraintSetFromCurrentForkPointBranch = SmartList()
                }

                stackForConstraintSetFromCurrentForkPointBranch!!.add(SmartList())

                block().also { anyForkSuccessful = anyForkSuccessful || it }

                stackForConstraintsSetsFromCurrentForkPoint!!.last()
                    .addIfNotNull(
                        stackForConstraintSetFromCurrentForkPointBranch?.popLast()?.takeIf { it.isNotEmpty() }?.toSet()
                    )
            }
        }

        fun hasConstraintsToProcess() = possibleNewConstraints != null

        fun setConstrainingTypesToPrintDebugInfo(lowerType: KotlinTypeMarker, upperType: KotlinTypeMarker) {
            baseLowerType = lowerType
            baseUpperType = upperType
        }

        fun runIsSubtypeOf(
            lowerType: KotlinTypeMarker,
            upperType: KotlinTypeMarker,
            shouldTryUseDifferentFlexibilityForUpperType: Boolean = false,
            isFromNullabilityConstraint: Boolean = false
        ) {
            fun isSubtypeOf(upperType: KotlinTypeMarker) =
                AbstractTypeChecker.isSubtypeOf(
                    this@TypeCheckerStateForConstraintInjector as TypeCheckerState,
                    lowerType,
                    upperType,
                    isFromNullabilityConstraint
                )

            if (!isSubtypeOf(upperType)) {
                // TODO: Get rid of this additional workarounds for flexible types once KT-59138 is fixed and
                //  the relevant feature for disabling it will be removed.
                // Also we should get rid of it once LanguageFeature.DontMakeExplicitJavaTypeArgumentsFlexible is removed
                if (shouldTryUseDifferentFlexibilityForUpperType && upperType.isRigidType()) {
                    /*
                     * Please don't reuse this logic.
                     * It's necessary to solve constraint systems when flexibility isn't propagated through a type variable.
                     * It's OK in the old inference because it uses already substituted types, that are with the correct flexibility.
                     */
                    require(upperType is RigidTypeMarker)
                    val flexibleUpperType = createTrivialFlexibleTypeOrSelf(upperType)
                    if (!isSubtypeOf(flexibleUpperType)) {
                        c.addError(NewConstraintError(lowerType, flexibleUpperType, position))
                    }
                } else {
                    c.addError(NewConstraintError(lowerType, upperType, position))
                }
            }
        }

        // from AbstractTypeCheckerContextForConstraintSystem
        override fun isMyTypeVariable(type: RigidTypeMarker): Boolean =
            c.allTypeVariables.containsKey(type.typeConstructor().unwrapStubTypeVariableConstructor())

        override fun addUpperConstraint(typeVariable: TypeConstructorMarker, superType: KotlinTypeMarker, isNoInfer: Boolean) =
            addConstraint(
                typeVariable, superType, UPPER,
                isFromNullabilityConstraint = false, isNoInfer = isNoInfer
            )

        override fun addLowerConstraint(
            typeVariable: TypeConstructorMarker,
            subType: KotlinTypeMarker,
            isFromNullabilityConstraint: Boolean,
            isNoInfer: Boolean,
        ) = addConstraint(typeVariable, subType, LOWER, isFromNullabilityConstraint, isNoInfer)

        override fun addEqualityConstraint(typeVariable: TypeConstructorMarker, type: KotlinTypeMarker) =
            addConstraint(
                typeVariable, type, EQUALITY,
                isFromNullabilityConstraint = false, isNoInfer = false
            )

        private fun isCapturedTypeFromSubtyping(type: KotlinTypeMarker): Boolean {
            val capturedType = type as? CapturedTypeMarker ?: return false

            if (capturedType.isOldCapturedType()) return false

            return when (capturedType.captureStatus()) {
                CaptureStatus.FROM_EXPRESSION -> false
                CaptureStatus.FOR_SUBTYPING -> true
                CaptureStatus.FOR_INCORPORATION ->
                    error("Captured type for incorporation shouldn't escape from incorporation: $type\n" + renderBaseConstraint())
            }
        }

        private fun addConstraint(
            typeVariableConstructor: TypeConstructorMarker,
            type: KotlinTypeMarker,
            kind: ConstraintKind,
            isFromNullabilityConstraint: Boolean,
            isNoInfer: Boolean,
        ) {
            val typeVariable = c.allTypeVariables[typeVariableConstructor.unwrapStubTypeVariableConstructor()]
                ?: error("Should by type variableConstructor: $typeVariableConstructor. ${c.allTypeVariables.values}")

            addNewIncorporatedConstraint(
                typeVariable,
                type,
                ConstraintContext(
                    kind = kind,
                    derivedFrom = currentDerivedFromSet,
                    isNullabilityConstraint = isFromNullabilityConstraint,
                    isNoInfer = isNoInfer
                )
            )
        }

        // from ConstraintIncorporator.Context
        override fun processNewInitialConstraintFromIncorporation(
            lowerType: KotlinTypeMarker,
            upperType: KotlinTypeMarker,
            shouldTryUseDifferentFlexibilityForUpperType: Boolean,
            newDerivedFrom: Set<TypeVariableMarker>,
            isFromNullabilityConstraint: Boolean,
            isFromDeclaredUpperBound: Boolean,
            isNoInfer: Boolean,
        ) = with(c) {
            // Avoid checking trivial incorporated constraints
            if (isK2) {
                if (lowerType == upperType) return
            } else {
                if (lowerType === upperType) return
            }
            if (lowerType.isAllowedType() && upperType.isAllowedType()) {
                withNewConfigurationForIncorporationConstraints(
                    newDerivedFromSet = newDerivedFrom,
                    isFromDeclaredUpperBound = isFromDeclaredUpperBound,
                    isNoInfer = isNoInfer,
                ) {
                    runIsSubtypeOf(lowerType, upperType, shouldTryUseDifferentFlexibilityForUpperType, isFromNullabilityConstraint)
                }
            }
        }

        private inline fun withNewConfigurationForIncorporationConstraints(
            newDerivedFromSet: Set<TypeVariableMarker>,
            isFromDeclaredUpperBound: Boolean,
            isNoInfer: Boolean,
            b: () -> Unit,
        ) {
            // No immediate recursive incorporation should happen, so `currentDerivedFromSet` would be reset at "finally"
            check(currentDerivedFromSet.isEmpty())

            try {
                currentDerivedFromSet = newDerivedFromSet
                isIncorporatingConstraintFromDeclaredUpperBound = isFromDeclaredUpperBound
                isIncorporatingConstraintFromNoInfer = isNoInfer
                b()
            } finally {
                // NB: `emptySet()` returns a singleton, so no excessive memory here
                currentDerivedFromSet = emptySet()
                isIncorporatingConstraintFromDeclaredUpperBound = false
                isIncorporatingConstraintFromNoInfer = false
            }
        }

        override fun addNewIncorporatedConstraint(
            typeVariable: TypeVariableMarker,
            type: KotlinTypeMarker,
            constraintContext: ConstraintContext
        ) {
            val (kind, derivedFrom, inputTypePosition, isNullabilityConstraint, isNoInfer) = constraintContext

            var targetType = type
            if (targetType.isUninferredParameter()) {
                // there already should be an error, so there is no point in reporting one more
                return
            }

            if (targetType.isError()) {
                c.addError(ConstrainingTypeIsError(typeVariable, targetType, position))
                return
            }

            if (type.contains(this::isCapturedTypeFromSubtyping)) {
                // TypeVariable <: type -> if TypeVariable <: subType => TypeVariable <: type
                if (kind == UPPER) {
                    val subType =
                        typeApproximator.approximateToSubType(type, TypeApproximatorConfiguration.SubtypeCapturedTypesApproximation)
                    if (subType != null) {
                        targetType = subType
                    }
                }

                if (kind == LOWER) {
                    val superType =
                        typeApproximator.approximateToSuperType(type, TypeApproximatorConfiguration.SubtypeCapturedTypesApproximation)
                    if (superType != null) { // todo rethink error reporting for Any cases
                        targetType = superType
                    }
                }

                if (targetType === type) {
                    c.addError(CapturedTypeFromSubtyping(typeVariable, type, position))
                    return
                }
            }

            val position = if (isIncorporatingConstraintFromDeclaredUpperBound) position.copy(isFromDeclaredUpperBound = true) else position

            val newConstraint = Constraint(
                kind, targetType, position,
                derivedFrom = derivedFrom,
                isNullabilityConstraint = isNullabilityConstraint,
                isNoInfer = isNoInfer || isIncorporatingConstraintFromNoInfer,
                inputTypePositionBeforeIncorporation = inputTypePosition,
            )

            addPossibleNewConstraint(typeVariable, newConstraint)
            inferenceLogger?.log(typeVariable, newConstraint, c)
        }

        override val allTypeVariablesWithConstraints: Collection<VariableWithConstraints>
            get() = c.notFixedTypeVariables.values

        override fun getVariablesWithConstraintsContainingGivenTypeVariable(
            variableConstructorMarker: TypeConstructorMarker
        ): Collection<VariableWithConstraints> =
            c.typeVariableDependencies[variableConstructorMarker]?.mapNotNull { c.notFixedTypeVariables[it] }
                ?: emptyList()

        override fun getTypeVariable(typeConstructor: TypeConstructorMarker): TypeVariableMarker? {
            val typeVariable = c.allTypeVariables[typeConstructor]
            if (typeVariable != null && !c.notFixedTypeVariables.containsKey(typeConstructor)) {
                fixedTypeVariable(typeVariable)
            }
            return typeVariable
        }

        override fun getConstraintsForVariable(typeVariable: TypeVariableMarker) =
            c.notFixedTypeVariables[typeVariable.freshTypeConstructor()]?.constraints
                ?: fixedTypeVariable(typeVariable)

        fun fixedTypeVariable(variable: TypeVariableMarker): Nothing {
            error(
                "Type variable $variable should not be fixed!\n" +
                        renderBaseConstraint()
            )
        }

        private fun renderBaseConstraint() = "Base constraint: $baseLowerType <: $baseUpperType from position: $position"
    }
}

data class ConstraintContext(
    val kind: ConstraintKind,
    val derivedFrom: Set<TypeVariableMarker>,
    val inputTypePositionBeforeIncorporation: OnlyInputTypeConstraintPosition? = null,
    val isNullabilityConstraint: Boolean,
    val isNoInfer: Boolean,
)

private typealias Stack<E> = MutableList<E>
