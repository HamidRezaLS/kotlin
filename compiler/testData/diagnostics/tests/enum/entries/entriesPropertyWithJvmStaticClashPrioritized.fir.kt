// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +EnumEntries +PrioritizedEnumEntries
// WITH_STDLIB
// FIR_DUMP

enum class A {
    ;

    companion object {
        @JvmStatic
        val entries = 0
    }
}

fun test() {
    A.entries

    with(A) {
        entries
    }

    A.Companion.entries
}

/* GENERATED_FIR_TAGS: companionObject, enumDeclaration, functionDeclaration, integerLiteral, lambdaLiteral,
objectDeclaration, propertyDeclaration */
