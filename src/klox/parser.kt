package klox

import klox.Expr.*
import klox.TokenType.*
import kotlin.collections.ArrayList

class Parser(private val tokens: List<Token>) {
	private var current = 0
	private var notBreakable = true
	private var notContinuable = true
	private var notInClass = true
	private var funcDepth = 0

	private class ParseError : RuntimeException()

	fun parse(): List<Stmt>? {
		val statements: MutableList<Stmt> = ArrayList()
		while (!isAtEnd()) {
			declaration()?.let { statements.add(it) }
		}

		return statements
	}

	private fun isAtEnd() = peek().type === EOF

	private fun peek() = tokens[current]

	private fun previous() = tokens[current - 1]

	private fun check(type: TokenType) =
		if (isAtEnd()) false else peek().type === type

	private fun advance(): Token {
		if (!isAtEnd()) current++
		return previous()
	}

	private fun consume(type: TokenType, message: String): Token {
		if (check(type)) return advance()
		throw error(peek(), message)
	}

	private fun match(vararg types: TokenType): Boolean {
		for (type in types) {
			if (check(type)) {
				advance()
				return true
			}
		}
		return false
	}

	private fun error(token: Token, message: String): Throwable {
		Lox.error(token, message)
		return ParseError()
	}

	private fun expression() = assignment()

	private fun primary(): Expr {
		return when {
			match(FALSE) -> Literal(false)
			match(TRUE) -> Literal(true)
			match(NIL) -> Literal(null)
			match(NUMBER, STRING) -> {
				Literal(previous().literal)
			}
			match(THIS) -> This(previous())
			match(IDENTIFIER) -> Variable(previous())
			match(LEFT_PAREN) -> {
				val expr = expression()
				consume(RIGHT_PAREN, "Expect ')' after expression.")
				Grouping(expr)
			}
			else -> throw ParseError()
		}
	}

	private fun call(): Expr {
		var expr = primary()

		while (true) {
			expr = if (match(LEFT_PAREN)) {
				val args = ArrayList<Expr>()
				do {
					if (args.size >= 255)
						error(peek(), "Cannot have more than 255 arguments.")
					try {
						args.add(expression())
					} catch (error: ParseError) {
					} // ignore - no arguments
				} while (match(COMMA))
				val paren = consume(RIGHT_PAREN, "Expect ')' after arguments")
				Call(expr, paren, args)
			} else if (match(DOT)) {
				val name = consume(IDENTIFIER, "Expect name after '.'")
				Get(expr, name)
			} else break
		}

		return expr
	}

	private fun unary(): Expr {
		if (match(BANG, MINUS)) {
			val operator = previous()
			val right = unary()
			return Unary(operator, right)
		}
		return call()
	}

	private fun multiplication(): Expr {
		var expr = unary()
		while (match(SLASH, STAR)) {
			val operator = previous()
			val right = unary()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun addition(): Expr {
		var expr = multiplication()
		while (match(MINUS, PLUS)) {
			val operator = previous()
			val right = multiplication()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun comparison(): Expr {
		var expr: Expr = addition()
		while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			val operator = previous()
			val right = addition()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun equality(): Expr {
		var expr: Expr = comparison()
		while (match(BANG_EQUAL, EQUAL_EQUAL)) {
			val operator = previous()
			val right = comparison()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun and(): Expr {
		var expr = equality()
		while (match(AND)) {
			val operator = previous()
			val right = and()
			expr = Logical(expr, operator, right)
		}
		return expr
	}

	private fun or(): Expr {
		var expr = and()
		while (match(OR)) {
			val operator = previous()
			val right = and()
			expr = Logical(expr, operator, right)
		}
		return expr
	}

	private fun assignment(): Expr {
		val expr = or()
		if (match(EQUAL)) {
			val equals = previous()
			val value = assignment()
			if (expr is Variable) {
				return Assign(expr.name, value)
			} else if (expr is Get) {
				return Set(expr.oobject, expr.name, value)
			}
			error(equals, "Illegal assignment target.")
		}
		return expr
	}

	private fun declaration(): Stmt? {
		return try {
			when {
				match(FUN) -> funDeclaration("function")
				match(CLASS) -> classDeclaration()
				match(LET) -> letDeclaration()
				else -> statement()
			}
		} catch (error: ParseError) {
			synchronize()
			null
		}
	}

	private fun statement(): Stmt {
		return when {
			match(IF) -> ifStatement()
			match(RETURN) -> returnStatement()
			match(FOR) -> forStatement()
			match(WHILE) -> whileStatement()
			match(BREAK) -> breakStatement()
			match(CONTINUE) -> continueStatement()
			match(LEFT_BRACE) -> Stmt.Block(block())
			else -> expressionStatement()
		}
	}

	private fun block(): List<Stmt> {
		notBreakable = false

		val stmts = ArrayList<Stmt>()
		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			declaration()?.let { stmts.add(it) }
		}

		consume(RIGHT_BRACE, "Expect '}' after block.")

		notBreakable = true
		return stmts
	}

	private fun expressionStatement(): Stmt {
		val expr = expression()
		consume(SEMICOLON, "Expect ';' after expression.")
		return Stmt.Expression(expr)
	}

	private fun letDeclaration(): Stmt {
		val name = consume(IDENTIFIER, "Expect variable name.")
		var initializer: Expr? = null
		if (match(EQUAL)) {
			initializer = expression()
		}
		consume(SEMICOLON, "Expect ';' after variable declaration.")
		return Stmt.Let(name, initializer)
	}

	private fun funDeclaration(kind: String, isStatic: Boolean = false): Stmt.Function {
		funcDepth += 1

		val name = consume(IDENTIFIER, "Expect $kind name.")
		consume(LEFT_PAREN, "Expect '(' after $kind name.")

		val params = ArrayList<Token>()
		if (!check(RIGHT_PAREN)) {
			do {
				if (params.size >= 255) error(peek(), "Cannot have more than 255 parameters.")
				params.add(consume(IDENTIFIER, "Expect parameter name."))
			} while (match(COMMA))
		}

		consume(RIGHT_PAREN, "Expect ')' after parameters.")
		consume(LEFT_BRACE, "Expect '{' before $kind body")

		val body = block()

		funcDepth -= 1
		return Stmt.Function(name, params, body, isStatic)
	}

	private fun classDeclaration(): Stmt {
		val name = consume(IDENTIFIER, "Expect class name.")
		consume(LEFT_BRACE, "Expect '{' before class body")

		notInClass = false

		val methods = mutableListOf<Stmt.Function>()
		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			methods.add(funDeclaration("method", match(STATIC)))
		}

		notInClass = true

		consume(RIGHT_BRACE, "Expect '}' after class body")

		return Stmt.Class(name, methods)
	}

	private fun ifStatement(): Stmt {
		consume(LEFT_PAREN, "Expect '(' after if.")
		val condition = expression()
		consume(RIGHT_PAREN, "Expect ')' after if condition.")


		notBreakable = false
		val thenBranch = statement()
		notBreakable = true

		var elseBranch: Stmt? = null
		if (match(ELSE)) {
			notBreakable = false
			elseBranch = statement()
			notBreakable = true
		}

		return Stmt.If(condition, thenBranch, elseBranch)
	}

	private fun whileStatement(): Stmt {
		consume(LEFT_PAREN, "Expect '(' after if.")
		val condition = expression()
		consume(RIGHT_PAREN, "Expect ')' after if condition.")

		notBreakable = false
		notContinuable = false

		val body = statement()

		notBreakable = true
		notContinuable = true

		return Stmt.While(condition, body)
	}

	private fun forStatement(): Stmt {
		consume(LEFT_PAREN, "Expect '(' after 'for'.")
		val initializer = when {
			match(SEMICOLON) -> null
			match(LET) -> letDeclaration()
			else -> expressionStatement()
		}

		val condition: Expr?
		if (match(SEMICOLON)) {
			condition = Literal(true)
		} else {
			condition = expression()
			consume(SEMICOLON, "Expect semicolon after for condition.")
		}

		val increment = when {
			match(RIGHT_PAREN) -> null
			else -> expression()
		}

		consume(RIGHT_PAREN, "Expect ')' after for clauses.")

		notBreakable = false
		notContinuable = false

		var body = statement()

		notBreakable = true
		notContinuable = true

		if (increment != null) body = Stmt.Block(listOf(body, Stmt.Expression(increment)))

		body = Stmt.While(condition, body)

		if (initializer != null) body = Stmt.Block(listOf(initializer, body))

		return body
	}

	private fun returnStatement(): Stmt {
		if (funcDepth == 0) error(previous(), "Illegal return statement")
		val value = if (match(SEMICOLON)) null else expression()
		return Stmt.Return(previous(), value)
	}

	private fun breakStatement(): Stmt {
		if (notBreakable) throw error(previous(), "Illegal break statement.")
		return Stmt.Break(previous())
	}

	private fun continueStatement(): Stmt {
		if (notContinuable) throw error(previous(), "Illegal continue statement.")
		return Stmt.Continue(previous())
	}

	private fun synchronize() {
		advance()
		while (!isAtEnd()) {
			if (previous().type === SEMICOLON) return
			when (peek().type) {
				CLASS, FUN, LET, FOR, IF, WHILE, PRINT, RETURN -> return
				else -> advance()
			}
		}
	}
}
