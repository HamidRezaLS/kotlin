// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-64891
// FIR_DUMP

fun test(f: (Int) -> Int) {
    2.<!NO_RECEIVER_ALLOWED!>(f)<!>()
}

typealias TA = Int.() -> Int

fun rest(f: TA) {
    2.(f)()
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, integerLiteral, typeAliasDeclaration, typeWithExtension */
