// LL_FIR_DIVERGENCE
// Not a real LL divergence, it's just tiered runners reporting errors from `BACKEND`
// LL_FIR_DIVERGENCE
// RUN_PIPELINE_TILL: BACKEND
// MODULE: m1-common
// FILE: common.kt
annotation class Ann(val p: String = "")
@Ann("")
expect fun explicitDefaultArgument()

// MODULE: m1-jvm()()(m1-common)
// FILE: jvm.kt
// No special handling for this case
@Ann
actual fun <!ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT!>explicitDefaultArgument<!>() {}

/* GENERATED_FIR_TAGS: actual, annotationDeclaration, expect, functionDeclaration, primaryConstructor,
propertyDeclaration, stringLiteral */
