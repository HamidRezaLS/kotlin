// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
interface X {
    fun foo(a : Int = 1)
}

interface Y {
    fun foo(a : Int = 1)
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED, MULTIPLE_DEFAULTS_INHERITED_FROM_SUPERTYPES_WHEN_NO_EXPLICIT_OVERRIDE!>class Z1<!> : X, Y {} // BUG
<!ABSTRACT_MEMBER_NOT_IMPLEMENTED, MULTIPLE_DEFAULTS_INHERITED_FROM_SUPERTYPES_WHEN_NO_EXPLICIT_OVERRIDE!>object Z1O<!> : X, Y {} // BUG

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, interfaceDeclaration, objectDeclaration */
