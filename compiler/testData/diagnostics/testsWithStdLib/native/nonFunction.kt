// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
<!WRONG_MODIFIER_TARGET!>external<!> class A

<!WRONG_MODIFIER_TARGET!>external<!> val foo: Int = 23

class B {
    <!WRONG_MODIFIER_TARGET!>external<!> class A

    <!WRONG_MODIFIER_TARGET!>external<!> val foo: Int = 23
}

/* GENERATED_FIR_TAGS: classDeclaration, integerLiteral, nestedClass, propertyDeclaration */
