// RUN_PIPELINE_TILL: FRONTEND
package a

fun foo(i: Int) = i

fun bar(l: Long) = l

fun main() {
    val i = <!INT_LITERAL_OUT_OF_RANGE!>111111111111111777777777777777<!>

    //todo add diagnostic text messages
    //report only 'The value is out of range'
    //not 'An integer literal does not conform to the expected type Int/Long'
    val l: Long = <!INT_LITERAL_OUT_OF_RANGE!>1111111111111117777777777777777<!>
    foo(<!INT_LITERAL_OUT_OF_RANGE!>11111111111111177777777777777<!>)
    bar(<!INT_LITERAL_OUT_OF_RANGE, TYPE_MISMATCH!>11111111111111177777777777777<!>)
}

/* GENERATED_FIR_TAGS: functionDeclaration, localProperty, propertyDeclaration */
