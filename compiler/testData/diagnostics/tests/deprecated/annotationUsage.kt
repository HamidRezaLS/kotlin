// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
@Deprecated("text")
annotation class obsolete()

@Deprecated("text")
annotation class obsoleteWithParam(val text: String)

@<!DEPRECATION!>obsolete<!> class Obsolete

@<!DEPRECATION!>obsoleteWithParam<!>("text") class Obsolete2

/* GENERATED_FIR_TAGS: annotationDeclaration, classDeclaration, primaryConstructor, propertyDeclaration, stringLiteral */
