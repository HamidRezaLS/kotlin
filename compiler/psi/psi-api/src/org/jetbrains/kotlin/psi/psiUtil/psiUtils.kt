/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi.psiUtil

import com.intellij.lang.ASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.SearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.PsiDiagnosticUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.PLUS
import org.jetbrains.kotlin.psi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.getChildren
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// NOTE: in this file we collect only LANGUAGE INDEPENDENT methods working with PSI and not modifying it

// ----------- Walking children/siblings/parents -------------------------------------------------------------------------------------------

/**
 * Returns all descendants of the given [this] in flatten form if it's a concatenation expression
 * with string literal arguments.
 * Otherwise, returns `null`.
 *
 * For instance, for the expression `"a0" /* comment before plus */ + /* comment after plus */ "a1"`
 * It returns `"a0"`, ws, /* comment before plus */, ws, `+`, ws, `/* comment after plus */`, ws, `"a1"`.
 *
 * @see [tryFlattenStringConcatenation] for more detail.
 */
@KtImplementationDetail
fun KtBinaryExpression.tryFlattenStringConcatenationDescendants(): List<PsiElement>? {
    return tryFlattenStringConcatenation(fullFidelity = true)
}

/**
 * Returns arguments of the given [this] if it's a concatenation expression with string literal arguments.
 * Otherwise, returns `null`.
 *
 * For instance, for the expression `"a0" /* comment before plus */ + /* comment after plus */ "a1"`
 * It returns `"a0"`, `"a1"`.
 *
 * @see [tryFlattenStringConcatenation] for more detail.
 */
@KtImplementationDetail
fun KtBinaryExpression.tryFlattenStringConcatenationArguments(): List<KtStringTemplateExpression>? {
    @Suppress("UNCHECKED_CAST")
    return tryFlattenStringConcatenation(fullFidelity = false) as? List<KtStringTemplateExpression>
}

/**
 * Emulates recursion using a stack to prevent StackOverflow exception on big string concatenation expressions like
 * `val x = "a0" + "a1" + ... + "a9999"` (it's relatively common in machine-generated code)

 * This method traverses the provided [this], tries to extract all string template nodes and returns
 * the list of nested expressions in direct order if the input `KtBinaryExpression` matches the string literals concatenation pattern.
 * Otherwise, it returns `null`.
 * The method handles nested expressions by pushing nodes onto an input stack and processing them iteratively.
 *
 * For instance, the "a" + "b" + "c" is represented as
 *
 * ```
 *          '+'(0)
 *      '+'(1)     'c'
 *  'a'        'b'
 * ```
 *
 *
 * The method returns `'a', 'b', 'c'` if [fullFidelity] is `false` (default)
 * But returns `'a', 'b', '+'(1), 'c', '+'(0)` and hidden tokens in between (whitespaces or comments) otherwise.
 * This is used when a full-fidelity tree structure is needed (see usages).
 */
@KtImplementationDetail
private fun KtBinaryExpression.tryFlattenStringConcatenation(fullFidelity: Boolean): List<PsiElement>? {
    // Optimization: don't allocate anything if the root expression doesn't match the string concatenation folding pattern
    if (operationToken != PLUS) return null

    val input = mutableListOf<PsiElement>().also { it.add(this) }
    val output = ArrayDeque<PsiElement>()

    while (true) {
        when (val node = input.removeLastOrNull() ?: break) {
            is KtBinaryExpression -> {
                var child = node.firstChild
                while (child != null) {
                    input.add(child)
                    child = child.nextSibling
                }
            }
            is KtStringTemplateExpression -> {
                output.addFirst(node)
            }
            is PsiWhiteSpace,
            is PsiComment
                -> {
                if (fullFidelity) {
                    output.addFirst(node)
                }
            }
            is KtOperationReferenceExpression -> {
                if (node.operationSignTokenType != PLUS) {
                    return null
                }

                if (fullFidelity) {
                    output.addFirst(node)
                }
            }
            else -> {
                return null
            }
        }
    }

    return output
}

val PsiElement.allChildren: PsiChildRange
    get() {
        val first = firstChild
        return if (first != null) PsiChildRange(first, lastChild) else PsiChildRange.EMPTY
    }

fun PsiElement.siblings(forward: Boolean = true, withItself: Boolean = true): Sequence<PsiElement> {
    return object : Sequence<PsiElement> {
        override fun iterator(): Iterator<PsiElement> {
            var next: PsiElement? = this@siblings
            return object : Iterator<PsiElement> {
                init {
                    if (!withItself) next()
                }

                override fun hasNext(): Boolean = next != null
                override fun next(): PsiElement {
                    val result = next ?: throw NoSuchElementException()
                    next = if (forward) result.nextSibling else result.prevSibling
                    return result
                }
            }
        }
    }
}

val PsiElement.parentsWithSelf: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

val PsiElement.parents: Sequence<PsiElement>
    get() = parentsWithSelf.drop(1)

fun PsiElement.prevLeaf(skipEmptyElements: Boolean = false): PsiElement? = PsiTreeUtil.prevLeaf(this, skipEmptyElements)

fun PsiElement.nextLeaf(skipEmptyElements: Boolean = false): PsiElement? = PsiTreeUtil.nextLeaf(this, skipEmptyElements)

val PsiElement.prevLeafs: Sequence<PsiElement>
    get() = generateSequence({ prevLeaf() }, { it.prevLeaf() })

val PsiElement.nextLeafs: Sequence<PsiElement>
    get() = generateSequence({ nextLeaf() }, { it.nextLeaf() })

fun PsiElement.prevLeaf(filter: (PsiElement) -> Boolean): PsiElement? {
    var leaf = prevLeaf()
    while (leaf != null && !filter(leaf)) {
        leaf = leaf.prevLeaf()
    }
    return leaf
}

fun PsiElement.nextLeaf(filter: (PsiElement) -> Boolean): PsiElement? {
    var leaf = nextLeaf()
    while (leaf != null && !filter(leaf)) {
        leaf = leaf.nextLeaf()
    }
    return leaf
}

fun <T : PsiElement> PsiElement.getParentOfTypes(strict: Boolean = false, vararg parentClasses: Class<out T>): T? {
    return getParentOfTypesAndPredicate(strict, *parentClasses) { true }
}

fun <T : PsiElement> PsiElement.getParentOfTypesAndPredicate(
    strict: Boolean = false, vararg parentClasses: Class<out T>, predicate: (T) -> Boolean
): T? {
    var element = if (strict) parent else this
    while (element != null) {
        @Suppress("UNCHECKED_CAST")
        when {
            (parentClasses.isEmpty() || parentClasses.any { parentClass -> parentClass.isInstance(element) }) && predicate(element as T) ->
                return element
            element is PsiFile ->
                return null
            else ->
                element = element.parent
        }
    }

    return null
}

fun <T : PsiElement> PsiElement.getNonStrictParentOfType(parentClass: Class<T>): T? {
    return PsiTreeUtil.getParentOfType(this, parentClass, false)
}

inline fun <reified T : PsiElement> PsiElement.getParentOfType(strict: Boolean): T? {
    return PsiTreeUtil.getParentOfType(this, T::class.java, strict)
}

inline fun <reified T : PsiElement, reified V : PsiElement> PsiElement.getParentOfTypes2(): PsiElement? {
    return PsiTreeUtil.getParentOfType(this, T::class.java, V::class.java)
}

inline fun <reified T : PsiElement, reified V : PsiElement, reified U : PsiElement> PsiElement.getParentOfTypes3(): PsiElement? {
    return PsiTreeUtil.getParentOfType(this, T::class.java, V::class.java, U::class.java)
}

inline fun <reified T : PsiElement> PsiElement.getParentOfType(strict: Boolean, vararg stopAt: Class<out PsiElement>): T? {
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    return PsiTreeUtil.getParentOfType(this, T::class.java, strict, *stopAt)
}

inline fun <reified T : PsiElement> PsiElement.getStrictParentOfType(): T? {
    return PsiTreeUtil.getParentOfType(this, T::class.java, true)
}

inline fun <reified T : PsiElement> PsiElement.getNonStrictParentOfType(): T? {
    return PsiTreeUtil.getParentOfType(this, T::class.java, false)
}

inline fun <reified T : PsiElement> PsiElement.getTopmostParentOfType(): T? {
    return PsiTreeUtil.getTopmostParentOfType(this, T::class.java)
}

inline fun <reified T : PsiElement> PsiElement.getChildOfType(): T? {
    return PsiTreeUtil.getChildOfType(this, T::class.java)
}

inline fun <reified T : PsiElement> PsiElement.getChildrenOfType(): Array<T> {
    return PsiTreeUtil.getChildrenOfType(this, T::class.java) ?: arrayOf()
}

fun PsiElement.getNextSiblingIgnoringWhitespaceAndComments(withItself: Boolean = false): PsiElement? {
    return siblings(withItself = withItself).filter { it !is PsiWhiteSpace && it !is PsiComment }.firstOrNull()
}

fun PsiElement.getNextSiblingIgnoringWhitespace(withItself: Boolean = false): PsiElement? {
    return siblings(withItself = withItself).filter { it !is PsiWhiteSpace }.firstOrNull()
}

fun PsiElement.getPrevSiblingIgnoringWhitespaceAndComments(withItself: Boolean = false): PsiElement? {
    return siblings(withItself = withItself, forward = false).filter { it !is PsiWhiteSpace && it !is PsiComment }.firstOrNull()
}

fun PsiElement.getPrevSiblingIgnoringWhitespace(withItself: Boolean = false): PsiElement? {
    return siblings(withItself = withItself, forward = false).filter { it !is PsiWhiteSpace }.firstOrNull()
}

inline fun <reified T : PsiElement> T.nextSiblingOfSameType() = PsiTreeUtil.getNextSiblingOfType(this, T::class.java)

inline fun <reified T : PsiElement> T.prevSiblingOfSameType() = PsiTreeUtil.getPrevSiblingOfType(this, T::class.java)

fun PsiElement?.isAncestor(element: PsiElement, strict: Boolean = false): Boolean {
    return PsiTreeUtil.isAncestor(this, element, strict)
}

fun <T : PsiElement> T.getIfChildIsInBranch(element: PsiElement, branch: T.() -> PsiElement?): T? {
    return if (branch().isAncestor(element)) this else null
}

fun <T : PsiElement> T.getIfChildIsInBranches(element: PsiElement, branches: T.() -> Iterable<PsiElement?>): T? {
    return if (branches().any { it.isAncestor(element) }) this else null
}

inline fun <reified T : PsiElement> PsiElement.getParentOfTypeAndBranch(strict: Boolean = false, noinline branch: T.() -> PsiElement?): T? {
    return getParentOfType<T>(strict)?.getIfChildIsInBranch(this, branch)
}

inline fun <reified T : PsiElement> PsiElement.getParentOfTypeAndBranches(
    strict: Boolean = false,
    noinline branches: T.() -> Iterable<PsiElement?>
): T? {
    return getParentOfType<T>(strict)?.getIfChildIsInBranches(this, branches)
}

tailrec fun PsiElement.getOutermostParentContainedIn(container: PsiElement): PsiElement? {
    val parent = parent
    return if (parent == container) this else parent?.getOutermostParentContainedIn(container)
}

fun PsiElement.isInsideOf(elements: Iterable<PsiElement>): Boolean = elements.any { it.isAncestor(this) }

fun PsiChildRange.trimWhiteSpaces(): PsiChildRange {
    if (first == null) return this
    return PsiChildRange(
        first.siblings().firstOrNull { it !is PsiWhiteSpace },
        last!!.siblings(forward = false).firstOrNull { it !is PsiWhiteSpace })
}

/**
 * See [unwrap()][org.jetbrains.kotlin.fir.builder.AbstractRawFirBuilder.unwrap]
 */
val UNWRAPPABLE_TOKEN_TYPES: Set<IElementType> = setOf(PARENTHESIZED, LABELED_EXPRESSION, ANNOTATED_EXPRESSION)

/**
 * This function should only be called for a source element corresponding to
 * an assignment/assignment operator call/increment or a decrement operator.
 */
fun PsiElement.getAssignmentLhsIfUnwrappable(): PsiElement? =
    when {
        // In `++(x)` the LHS source `(x)` is the last child
        elementType == PREFIX_EXPRESSION -> children.lastOrNull()
        // In `(x)++` or `(x) = ...` the LHS source is the first child
        else -> children.firstOrNull()
    }.takeIf {
        it?.elementType in UNWRAPPABLE_TOKEN_TYPES
    }

/**
 * This function should only be called for a source element corresponding to
 * an assignment/assignment operator call/increment or a decrement operator.
 */
fun LighterASTNode.getAssignmentLhsIfUnwrappable(tree: FlyweightCapableTreeStructure<LighterASTNode>): LighterASTNode? =
    when {
        // In `++(x)` the LHS source `(x)` is the last child
        tokenType == PREFIX_EXPRESSION -> getChildren(tree).lastOrNull()
        // In `(x)++` or `(x) = ...` the LHS source is the first child
        else -> getChildren(tree).firstOrNull()
    }.takeIf {
        it?.tokenType in UNWRAPPABLE_TOKEN_TYPES
    }

/**
 * This function should only be called for a source element corresponding to
 * an assignment/assignment operator call/increment or a decrement operator.
 */
fun KtSourceElement?.hasUnwrappableAsAssignmentLhs(): Boolean {
    if (this == null) {
        return false
    }

    val node = psi?.getAssignmentLhsIfUnwrappable()
        ?: lighterASTNode.getAssignmentLhsIfUnwrappable(treeStructure)

    return node != null
}

// -------------------- Recursive tree visiting --------------------------------------------------------------------------------------------

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfType(noinline action: (T) -> Unit) {
    forEachDescendantOfType({ true }, action)
}

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfType(
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline action: (T) -> Unit
) {
    checkDecompiledText()
    this.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (canGoInside(element)) {
                super.visitElement(element)
            }

            if (element is T) {
                action(element)
            }
        }
    })
}

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfTypeInPreorder(noinline action: (T) -> Unit) {
    forEachDescendantOfTypeInPreorder({ true }, action)
}

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfTypeInPreorder(
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline action: (T) -> Unit,
) {
    checkDecompiledText()
    this.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is T) {
                action(element)
            }

            if (canGoInside(element)) {
                super.visitElement(element)
            }
        }
    })
}

inline fun <reified T : PsiElement> PsiElement.anyDescendantOfType(noinline predicate: (T) -> Boolean = { true }): Boolean {
    return findDescendantOfType(predicate) != null
}

inline fun <reified T : PsiElement> PsiElement.anyDescendantOfType(
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline predicate: (T) -> Boolean = { true }
): Boolean {
    return findDescendantOfType(canGoInside, predicate) != null
}

inline fun <reified T : PsiElement> PsiElement.findDescendantOfType(noinline predicate: (T) -> Boolean = { true }): T? {
    return findDescendantOfType({ true }, predicate)
}

inline fun <reified T : PsiElement> PsiElement.findDescendantOfType(
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline predicate: (T) -> Boolean = { true }
): T? {
    checkDecompiledText()
    var result: T? = null
    this.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is T && predicate(element)) {
                result = element
                stopWalking()
                return
            }

            if (canGoInside(element)) {
                super.visitElement(element)
            }
        }
    })
    return result
}

fun PsiElement.checkDecompiledText() {
    val file = containingFile
    if (file is KtFile && file.isCompiled && file.stub != null) {
        error("Attempt to load decompiled text, please use stubs instead. Decompile process might be slow and should be avoided")
    }
}

inline fun <reified T : PsiElement> PsiElement.collectDescendantsOfType(noinline predicate: (T) -> Boolean = { true }): List<T> {
    return collectDescendantsOfType({ true }, predicate)
}

inline fun <reified T : PsiElement> PsiElement.collectDescendantsOfType(
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline predicate: (T) -> Boolean = { true }
): List<T> = collectDescendantsOfTypeTo(ArrayList(), canGoInside, predicate)

inline fun <reified T : PsiElement, C : MutableCollection<T>> PsiElement.collectDescendantsOfTypeTo(
    to: C,
    crossinline canGoInside: (PsiElement) -> Boolean,
    noinline predicate: (T) -> Boolean = { true }
): C {
    forEachDescendantOfType<T>(canGoInside) {
        if (predicate(it)) {
            to.add(it)
        }
    }
    return to
}

// ----------- Working with offsets, ranges and texts ----------------------------------------------------------------------------------------------

val PsiElement.startOffset: Int
    get() = textRange.startOffset

val PsiElement.endOffset: Int
    get() = textRange.endOffset

val KtPureElement.pureStartOffset: Int
    get() = psiOrParent.textRangeWithoutComments.startOffset

val KtPureElement.pureEndOffset: Int
    get() = psiOrParent.textRangeWithoutComments.endOffset

val PsiElement.startOffsetSkippingComments: Int
    get() {
        if (!startsWithComment()) return startOffset // fastpath
        val firstNonCommentChild = generateSequence(firstChild) { it.nextSibling }
            .firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
        return firstNonCommentChild?.startOffset ?: startOffset
    }

fun PsiElement.getStartOffsetIn(ancestor: PsiElement): Int {
    var offset = 0
    var parent = this
    while (parent != ancestor) {
        offset += parent.startOffsetInParent
        parent = parent.parent
    }
    return offset
}

fun TextRange.containsInside(offset: Int): Boolean = startOffset < offset && offset < endOffset

val PsiChildRange.textRange: TextRange?
    get() {
        if (isEmpty) return null
        return TextRange(first!!.startOffset, last!!.endOffset)
    }

fun PsiChildRange.getText(): String {
    if (isEmpty) return ""
    return this.map { it.text }.joinToString("")
}

fun PsiFile.elementsInRange(range: TextRange): List<PsiElement> {
    var offset = range.startOffset
    val result = ArrayList<PsiElement>()
    while (offset < range.endOffset) {
        val currentRange = TextRange(offset, range.endOffset)
        val leaf = findFirstLeafWhollyInRange(this, currentRange) ?: break

        val element = leaf
            .parentsWithSelf
            .first {
                val parent = it.parent
                it is PsiFile || parent.textRange !in currentRange
            }
        result.add(element)

        offset = element.endOffset
    }
    return result
}

private fun findFirstLeafWhollyInRange(file: PsiFile, range: TextRange): PsiElement? {
    var element = file.findElementAt(range.startOffset) ?: return null
    var elementRange = element.textRange
    if (elementRange.startOffset < range.startOffset) {
        element = element.nextLeaf(skipEmptyElements = true) ?: return null
        elementRange = element.textRange
    }
    assert(elementRange.startOffset >= range.startOffset)
    return if (elementRange.endOffset <= range.endOffset) element else null
}

val PsiElement.textRangeWithoutComments: TextRange
    get() = if (!startsWithComment()) textRange else TextRange(startOffsetSkippingComments, endOffset)

fun PsiElement.startsWithComment(): Boolean = firstChild is PsiComment


// ---------------------------------- Debug/logging ----------------------------------------------------------------------------------------

fun PsiElement.getElementTextWithContext(): String = org.jetbrains.kotlin.utils.getElementTextWithContext(this)

fun PsiElement.getTextWithLocation(): String = "'${this.text}' at ${PsiDiagnosticUtils.atLocation(this)}"

fun replaceFileAnnotationList(file: KtFile, annotationList: KtFileAnnotationList): KtFileAnnotationList {
    if (file.fileAnnotationList != null) {
        return file.fileAnnotationList!!.replace(annotationList) as KtFileAnnotationList
    }

    val beforeAnchor: PsiElement? = when {
        file.packageDirective?.packageKeyword != null -> file.packageDirective!!
        file.importList != null -> file.importList!!
        file.declarations.firstOrNull() != null -> file.declarations.first()
        else -> null
    }

    if (beforeAnchor != null) {
        return file.addBefore(annotationList, beforeAnchor) as KtFileAnnotationList
    }

    if (file.lastChild == null) {
        return file.add(annotationList) as KtFileAnnotationList
    }

    return file.addAfter(annotationList, file.lastChild) as KtFileAnnotationList
}

// -----------------------------------------------------------------------------------------------------------------------------------------

operator fun SearchScope.contains(element: PsiElement): Boolean = PsiSearchScopeUtil.isInScope(this, element)

@Deprecated(
    "Use only in 'kotlin' repo until the alternative method from 'com.intellij.psi' package becomes available from the IJ platform",
    ReplaceWith("this.createSmartPointer()", "com.intellij.psi.createSmartPointer"),
)
fun <E : PsiElement> E.createSmartPointer(): SmartPsiElementPointer<E> =
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)

fun PsiElement.before(element: PsiElement) = textRange.endOffset <= element.textRange.startOffset

inline fun <reified T : PsiElement> PsiElement.getLastParentOfTypeInRow() = parents.takeWhile { it is T }.lastOrNull() as? T

inline fun <reified T : PsiElement> PsiElement.getLastParentOfTypeInRowWithSelf() = parentsWithSelf
    .takeWhile { it is T }.lastOrNull() as? T

fun KtModifierListOwner.hasExpectModifier() = hasModifier(KtTokens.EXPECT_KEYWORD)
fun KtModifierList.hasExpectModifier() = hasModifier(KtTokens.EXPECT_KEYWORD)

fun KtModifierListOwner.hasActualModifier() = hasModifier(KtTokens.ACTUAL_KEYWORD)
fun KtModifierList.hasActualModifier() = hasModifier(KtTokens.ACTUAL_KEYWORD)
fun KtModifierList.hasSuspendModifier() = hasModifier(KtTokens.SUSPEND_KEYWORD)

fun KtModifierList.hasFunModifier() = hasModifier(KtTokens.FUN_KEYWORD)

fun KtModifierList.hasValueModifier() = hasModifier(KtTokens.VALUE_KEYWORD)

fun KtModifierListOwner.hasInnerModifier() = hasModifier(KtTokens.INNER_KEYWORD)

fun ASTNode.children() = generateSequence(firstChildNode) { node -> node.treeNext }
fun ASTNode.parents() = generateSequence(treeParent) { node -> node.treeParent }

fun ASTNode.siblings(forward: Boolean = true): Sequence<ASTNode> {
    if (forward) {
        return generateSequence(treeNext) { it.treeNext }
    } else {
        return generateSequence(treePrev) { it.treePrev }
    }
}

fun ASTNode.leaves(forward: Boolean = true): Sequence<ASTNode> {
    if (forward) {
        return generateSequence(TreeUtil.nextLeaf(this)) { TreeUtil.nextLeaf(it) }
    } else {
        return generateSequence(TreeUtil.prevLeaf(this)) { TreeUtil.prevLeaf(it) }
    }
}

fun ASTNode.closestPsiElement(): PsiElement? {
    var node = this
    while (node.psi == null) {
        node = node.treeParent
    }
    return node.psi
}

fun LazyParseablePsiElement.getContainingKtFile(): KtFile {

    val file = this.containingFile

    if (file is KtFile) return file

    val fileString = if (file != null && file.isValid) file.text else ""
    throw IllegalStateException("KtElement not inside KtFile: $file with text \"$fileString\" for element $this of type ${this::class.java} node = ${this.node}")
}

@OptIn(ExperimentalContracts::class)
fun KtExpression.isNull(): Boolean {
    contract {
        returns(true) implies (this@isNull is KtConstantExpression)
    }
    return this is KtConstantExpression && this.node.elementType == KtNodeTypes.NULL
}

fun PsiElement?.unwrapParenthesesLabelsAndAnnotations(): PsiElement? {
    var unwrapped = this
    while (true) {
        unwrapped = when (unwrapped) {
            is KtParenthesizedExpression -> unwrapped.expression
            is KtLabeledExpression -> unwrapped.baseExpression
            is KtAnnotatedExpression -> unwrapped.baseExpression
            else -> return unwrapped
        }
    }
}

fun PsiElement.unwrapParenthesesLabelsAndAnnotationsDeeply(): PsiElement {
    var current: PsiElement = this
    var unwrapped: PsiElement?

    do {
        unwrapped = current.parent?.unwrapParenthesesLabelsAndAnnotations()
        current = current.parent
    } while (unwrapped != current)

    return unwrapped
}
