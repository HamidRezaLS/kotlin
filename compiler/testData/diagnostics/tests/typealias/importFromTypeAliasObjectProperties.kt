// RUN_PIPELINE_TILL: FRONTEND
// FILE: 1.kt
package objectProperties

typealias ObjectWithProps = A

object A {
    val a = 10
}

// FILE: 2.kt
import objectProperties.ObjectWithProps.a

/* GENERATED_FIR_TAGS: integerLiteral, objectDeclaration, propertyDeclaration, typeAliasDeclaration */
