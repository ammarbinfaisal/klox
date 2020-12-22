package klox

import klox.TokenType.*

class Scanner(private val source: String) {
	private var start = 0
	private var current = 0
	private var line = 1
	private val tokens = mutableListOf<Token>()

	private	val keywords = hashMapOf(
		"and" to AND,
		"break" to BREAK,
		"class" to CLASS,
		"continue" to CONTINUE,
		"else" to ELSE,
		"false" to FALSE,
		"for" to FOR,
		"fun" to FUN,
		"if" to IF,
		"let" to LET,
		"nil" to NIL,
		"or" to OR,
		"return" to RETURN,
		"super" to SUPER,
		"static" to STATIC,
		"this" to THIS,
		"true" to TRUE,
		"while" to WHILE
	)

	fun scanTokens(): List<Token> {
		while (!isAtEnd()) {
			start = current
			scanToken()
		}

		tokens.add(Token(EOF, "", null, line))
		return tokens
	}

	private fun scanToken() {
		when (val c = advance()) {
			'(' -> addToken(LEFT_PAREN)
			')' -> addToken(RIGHT_PAREN)
			'{' -> addToken(LEFT_BRACE)
			'}' -> addToken(RIGHT_BRACE)
			',' -> addToken(COMMA)
			'.' -> addToken(DOT)
			'-' -> addToken(MINUS)
			'+' -> addToken(PLUS)
			';' -> addToken(SEMICOLON)
			'*' -> addToken(STAR)
			'!' -> addToken(if(match('=')) BANG_EQUAL else BANG)
			'=' -> addToken(if(match('=')) EQUAL_EQUAL else EQUAL)
			'>' -> addToken(if(match('=')) GREATER_EQUAL else GREATER)
			'<' -> addToken(if(match('=')) LESS_EQUAL else LESS)
			'/' -> {
				if(match('/')) {
					while (peek() != '\n' && !isAtEnd()) advance()
				} else {
					addToken(SLASH)
				}
			}
			'"' -> parseString()
			in '0'..'9' -> parseNumber()
			' ', '\t', '\r' -> {}
			'\n' -> ++line
			else -> {
				if(isAlpha(c)) parseIdentifier()
				else Lox.error(line, "Unexpected character -> $c")
			}
		}
	}

	private fun match(c: Char): Boolean {
		if(isAtEnd()) return false
		if(source[current] == c) {
			++current
			return true
		}
		return false
	}

	private fun advance(): Char {
		++current
		return source[current - 1]
	}

	private fun peek(): Char {
		if(isAtEnd()) return '\u0000'
		return source[current]
	}

	private fun peekNext(): Char {
		if(current < source.length) return source[current+1]
		return '\u0000'
	}

	private fun isAtEnd(): Boolean {
		return current >= source.length
	}

	private fun isDigit(c: Char): Boolean = c in '0'..'9'

	private fun isAlpha(c: Char): Boolean = c.toLowerCase() in 'a'..'z'

	private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)

	private fun parseString() {
		while(peek() != '"' && !isAtEnd()) {
			if(peek() == '\n') ++line
			advance()
		}

		if (isAtEnd()) {
			return Lox.error(line, "Unterminated string.")
		}

		advance()

		val value = source.substring(start + 1, current - 1)
		addToken(STRING, value)
	}

	private fun parseNumber() {
		while(isDigit(peek())) advance()

		if(peek() == '.' && isDigit(peekNext())) {
			advance()
			while(isDigit(peek())) advance()
		}

		val value =  source.substring(start, current).toDouble()
		addToken(NUMBER, value)
	}

	private fun parseIdentifier() {
		while(isAlphaNumeric(peek())) advance()

		val text = source.substring(start, current)
		var type = keywords[text]

		if(type == null) type = IDENTIFIER

		addToken(type)
	}

	private fun addToken(type: TokenType, literal: Any? = null) {
		val text = source.substring(start, current)
		tokens.add(Token(type, text, literal, line))
	}
}
