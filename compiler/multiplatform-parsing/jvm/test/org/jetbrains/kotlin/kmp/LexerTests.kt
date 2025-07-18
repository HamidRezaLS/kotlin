/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kmp

import com.intellij.psi.tree.IElementType
import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.kotlin.kmp.infra.NewTestLexer
import org.jetbrains.kotlin.kmp.infra.OldTestLexer
import org.jetbrains.kotlin.kmp.infra.TestToken
import org.junit.jupiter.api.Test

class LexerTests : AbstractRecognizerTests<IElementType, SyntaxElementType, TestToken<IElementType>, TestToken<SyntaxElementType>>() {
    companion object {
        init {
            // Make sure the static declarations are initialized before time measurements to get more refined results
            initializeLexers()
        }

        fun initializeLexers() {
            org.jetbrains.kotlin.lexer.KtTokens.EOF
            org.jetbrains.kotlin.kdoc.lexer.KDocTokens.START

            org.jetbrains.kotlin.kmp.lexer.KtTokens.EOF
            org.jetbrains.kotlin.kmp.lexer.KDocTokens.START
        }
    }

    override fun recognizeOldSyntaxElement(fileName: String, text: String): TestToken<IElementType> = OldTestLexer().tokenize(text)
    override fun recognizeNewSyntaxElement(fileName: String, text: String): TestToken<SyntaxElementType> = NewTestLexer().tokenize(text)

    override val recognizerName: String = "lexer"
    override val recognizerSyntaxElementName: String = "token"
    override val expectedExampleDump: String = """fun [1:1..4)
WHITE_SPACE ` ` [1:4..5)
IDENTIFIER `main` [1:5..9)
LPAR `(` [1:9..10)
RPAR `)` [1:10..11)
WHITE_SPACE ` ` [1:11..12)
LBRACE `{` [1:12..13)
WHITE_SPACE [1:13..2:5)
IDENTIFIER `println` [2:5..12)
LPAR `(` [2:12..13)
OPEN_QUOTE `"` [2:13..14)
REGULAR_STRING_PART `Hello, World!` [2:14..27)
CLOSING_QUOTE `"` [2:27..28)
RPAR `)` [2:28..29)
WHITE_SPACE [2:29..3:1)
RBRACE `}` [3:1..2)
WHITE_SPACE [3:2..5:1)
class [5:1..6)
WHITE_SPACE ` ` [5:6..7)
IDENTIFIER `C` [5:7..8)
LPAR `(` [5:8..9)
val [5:9..12)
WHITE_SPACE ` ` [5:12..13)
IDENTIFIER `x` [5:13..14)
COLON `:` [5:14..15)
WHITE_SPACE ` ` [5:15..16)
IDENTIFIER `Int` [5:16..19)
RPAR `)` [5:19..20)
WHITE_SPACE [5:20..7:1)
KDoc [7:1..10:4)
  KDOC_START `/**` [7:1..4)
  WHITE_SPACE [7:4..8:2)
  KDOC_LEADING_ASTERISK `*` [8:2..3)
  KDOC_TEXT ` ` [8:3..4)
  KDOC_TAG_NAME `@param` [8:4..10)
  WHITE_SPACE ` ` [8:10..11)
  KDOC_MARKDOWN_LINK `[C.x]` [8:11..16)
    LBRACKET `[` [8:11..12)
    IDENTIFIER `C` [8:12..13)
    DOT `.` [8:13..14)
    IDENTIFIER `x` [8:14..15)
    RBRACKET `]` [8:15..16)
  WHITE_SPACE ` ` [8:16..17)
  KDOC_TEXT `Some parameter.` [8:17..32)
  WHITE_SPACE [8:32..9:2)
  KDOC_LEADING_ASTERISK `*` [9:2..3)
  KDOC_TEXT ` ` [9:3..4)
  KDOC_TAG_NAME `@return` [9:4..11)
  WHITE_SPACE ` ` [9:11..12)
  KDOC_MARKDOWN_LINK `[Exception]` [9:12..23)
    LBRACKET `[` [9:12..13)
    IDENTIFIER `Exception` [9:13..22)
    RBRACKET `]` [9:22..23)
  WHITE_SPACE [9:23..10:2)
  KDOC_END `*/` [10:2..4)
WHITE_SPACE [10:4..11:1)
fun [11:1..4)
WHITE_SPACE ` ` [11:4..5)
IDENTIFIER `test` [11:5..9)
LPAR `(` [11:9..10)
IDENTIFIER `p` [11:10..11)
COLON `:` [11:11..12)
WHITE_SPACE ` ` [11:12..13)
IDENTIFIER `String` [11:13..19)
RPAR `)` [11:19..20)
WHITE_SPACE ` ` [11:20..21)
LBRACE `{` [11:21..22)
WHITE_SPACE [11:22..12:5)
val [12:5..8)
WHITE_SPACE ` ` [12:8..9)
IDENTIFIER `badCharacter` [12:9..21)
WHITE_SPACE ` ` [12:21..22)
EQ `=` [12:22..23)
WHITE_SPACE ` ` [12:23..24)
BAD_CHARACTER `^` [12:24..25)
WHITE_SPACE [12:25..13:5)
throw [13:5..10)
WHITE_SPACE ` ` [13:10..11)
IDENTIFIER `Exception` [13:11..20)
LPAR `(` [13:20..21)
RPAR `)` [13:21..22)
WHITE_SPACE [13:22..14:1)
RBRACE `}` [14:1..2)"""

    override val expectedExampleSyntaxElementsNumber: Long = 83

    override val expectedDumpOnWindowsNewLine: String = """BAD_CHARACTER [1:1..2)
WHITE_SPACE [1:2..2:1)"""

    @Test
    fun testIdentifierWithBackticks() {
        checkOnKotlinCode(TestData.IDENTIFIER_WITH_BACKTICKS_IN_KDOC, $$"""KDoc [1:1..17:4)
  KDOC_START `/**` [1:1..4)
  WHITE_SPACE [1:4..2:2)
  KDOC_LEADING_ASTERISK `*` [2:2..3)
  WHITE_SPACE [2:3..3:2)
  KDOC_LEADING_ASTERISK `*` [3:2..3)
  KDOC_TEXT ` ` [3:3..4)
  KDOC_MARKDOWN_LINK `[`top level`]` [3:4..17)
    LBRACKET `[` [3:4..5)
    IDENTIFIER ``top level`` [3:5..16)
    RBRACKET `]` [3:16..17)
  WHITE_SPACE [3:17..4:2)
  KDOC_LEADING_ASTERISK `*` [4:2..3)
  KDOC_TEXT ` [top level]` [4:3..15)
  WHITE_SPACE [4:15..5:2)
  KDOC_LEADING_ASTERISK `*` [5:2..3)
  KDOC_TEXT ` [O.with space]` [5:3..18)
  WHITE_SPACE [5:18..6:2)
  KDOC_LEADING_ASTERISK `*` [6:2..3)
  KDOC_TEXT ` ` [6:3..4)
  KDOC_MARKDOWN_LINK `[O.`with space`]` [6:4..20)
    LBRACKET `[` [6:4..5)
    IDENTIFIER `O` [6:5..6)
    DOT `.` [6:6..7)
    IDENTIFIER ``with space`` [6:7..19)
    RBRACKET `]` [6:19..20)
  WHITE_SPACE [6:20..7:2)
  KDOC_LEADING_ASTERISK `*` [7:2..3)
  KDOC_TEXT ` ` [7:3..4)
  KDOC_TAG_NAME `@see` [7:4..8)
  WHITE_SPACE ` ` [7:8..9)
  KDOC_MARKDOWN_LINK `O.with` [7:9..15)
    IDENTIFIER `O` [7:9..10)
    DOT `.` [7:10..11)
    IDENTIFIER `with` [7:11..15)
  WHITE_SPACE ` ` [7:15..16)
  KDOC_TEXT `space` [7:16..21)
  WHITE_SPACE [7:21..8:2)
  KDOC_LEADING_ASTERISK `*` [8:2..3)
  KDOC_TEXT ` ` [8:3..4)
  KDOC_TAG_NAME `@see` [8:4..8)
  WHITE_SPACE ` ` [8:8..9)
  KDOC_MARKDOWN_LINK `O.`with space`` [8:9..23)
    IDENTIFIER `O` [8:9..10)
    DOT `.` [8:10..11)
    IDENTIFIER ``with space`` [8:11..23)
  WHITE_SPACE [8:23..9:2)
  KDOC_LEADING_ASTERISK `*` [9:2..3)
  KDOC_TEXT ` ` [9:3..4)
  KDOC_MARKDOWN_LINK `[O.without_space]` [9:4..21)
    LBRACKET `[` [9:4..5)
    IDENTIFIER `O` [9:5..6)
    DOT `.` [9:6..7)
    IDENTIFIER `without_space` [9:7..20)
    RBRACKET `]` [9:20..21)
  WHITE_SPACE [9:21..10:2)
  KDOC_LEADING_ASTERISK `*` [10:2..3)
  KDOC_TEXT ` ` [10:3..4)
  KDOC_MARKDOWN_LINK `[O.`without_space`]` [10:4..23)
    LBRACKET `[` [10:4..5)
    IDENTIFIER `O` [10:5..6)
    DOT `.` [10:6..7)
    IDENTIFIER ``without_space`` [10:7..22)
    RBRACKET `]` [10:22..23)
  WHITE_SPACE [10:23..11:2)
  KDOC_LEADING_ASTERISK `*` [11:2..3)
  KDOC_TEXT ` ` [11:3..4)
  KDOC_TAG_NAME `@see` [11:4..8)
  WHITE_SPACE ` ` [11:8..9)
  KDOC_MARKDOWN_LINK `O.without_space` [11:9..24)
    IDENTIFIER `O` [11:9..10)
    DOT `.` [11:10..11)
    IDENTIFIER `without_space` [11:11..24)
  WHITE_SPACE [11:24..12:2)
  KDOC_LEADING_ASTERISK `*` [12:2..3)
  KDOC_TEXT ` ` [12:3..4)
  KDOC_TAG_NAME `@see` [12:4..8)
  WHITE_SPACE ` ` [12:8..9)
  KDOC_MARKDOWN_LINK `O.`without_space`` [12:9..26)
    IDENTIFIER `O` [12:9..10)
    DOT `.` [12:10..11)
    IDENTIFIER ``without_space`` [12:11..26)
  WHITE_SPACE [12:26..13:2)
  KDOC_LEADING_ASTERISK `*` [13:2..3)
  WHITE_SPACE [13:3..14:2)
  KDOC_LEADING_ASTERISK `*` [14:2..3)
  KDOC_TEXT ` // Resolve incorrect code for completion` [14:3..44)
  WHITE_SPACE [14:44..15:2)
  KDOC_LEADING_ASTERISK `*` [15:2..3)
  KDOC_TEXT ` ` [15:3..4)
  KDOC_MARKDOWN_LINK `[O.]` [15:4..8)
    LBRACKET `[` [15:4..5)
    IDENTIFIER `O` [15:5..6)
    DOT `.` [15:6..7)
    RBRACKET `]` [15:7..8)
  WHITE_SPACE [15:8..16:2)
  KDOC_LEADING_ASTERISK `*` [16:2..3)
  KDOC_TEXT ` ` [16:3..4)
  KDOC_TAG_NAME `@see` [16:4..8)
  WHITE_SPACE ` ` [16:8..9)
  KDOC_MARKDOWN_LINK `O.` [16:9..11)
    IDENTIFIER `O` [16:9..10)
    DOT `.` [16:10..11)
  WHITE_SPACE [16:11..17:2)
  KDOC_END `*/` [17:2..4)
WHITE_SPACE [17:4..18:1)
fun [18:1..4)
WHITE_SPACE ` ` [18:4..5)
IDENTIFIER `main` [18:5..9)
LPAR `(` [18:9..10)
RPAR `)` [18:10..11)
WHITE_SPACE ` ` [18:11..12)
LBRACE `{` [18:12..13)
WHITE_SPACE [18:13..19:1)
RBRACE `}` [19:1..2)
WHITE_SPACE [19:2..21:1)
fun [21:1..4)
WHITE_SPACE ` ` [21:4..5)
IDENTIFIER ``top level`` [21:5..16)
LPAR `(` [21:16..17)
RPAR `)` [21:17..18)
WHITE_SPACE ` ` [21:18..19)
EQ `=` [21:19..20)
WHITE_SPACE ` ` [21:20..21)
IDENTIFIER `Unit` [21:21..25)
WHITE_SPACE [21:25..23:1)
object [23:1..7)
WHITE_SPACE ` ` [23:7..8)
IDENTIFIER `O` [23:8..9)
WHITE_SPACE ` ` [23:9..10)
LBRACE `{` [23:10..11)
WHITE_SPACE [23:11..24:5)
fun [24:5..8)
WHITE_SPACE ` ` [24:8..9)
IDENTIFIER ``with space`` [24:9..21)
LPAR `(` [24:21..22)
RPAR `)` [24:22..23)
WHITE_SPACE ` ` [24:23..24)
EQ `=` [24:24..25)
WHITE_SPACE ` ` [24:25..26)
IDENTIFIER `Unit` [24:26..30)
WHITE_SPACE [24:30..25:5)
fun [25:5..8)
WHITE_SPACE ` ` [25:8..9)
IDENTIFIER `without_space` [25:9..22)
LPAR `(` [25:22..23)
RPAR `)` [25:23..24)
WHITE_SPACE ` ` [25:24..25)
EQ `=` [25:25..26)
WHITE_SPACE ` ` [25:26..27)
IDENTIFIER `Unit` [25:27..31)
WHITE_SPACE [25:31..26:1)
RBRACE `}` [26:1..2)"""
        )
    }
}