// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class D<A : D<A, <!UPPER_BOUND_VIOLATED!>String<!>>, B : D<A, B>>

/* GENERATED_FIR_TAGS: classDeclaration, typeConstraint, typeParameter */
