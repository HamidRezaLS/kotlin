// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +UnitConversionsOnArbitraryExpressions
// DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_EXPRESSION

fun interface KRunnable {
    fun run()
}

fun foo(r: KRunnable) {}

abstract class SubInt : () -> Int

fun test(f: () -> Int, s: SubInt) {
    foo(f)
    foo(s)
}

/* GENERATED_FIR_TAGS: classDeclaration, funInterface, functionDeclaration, functionalType, interfaceDeclaration,
samConversion */
