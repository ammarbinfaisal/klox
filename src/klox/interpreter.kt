package klox

import klox.TokenType.*

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
	private val globals = Environment()
	private var env = globals
	private val locals = HashMap<Expr, Int>()

	init {
		globals.define("clock", object : LoxCallable {
			override fun arity() = 0

			override fun call(interpreter: Interpreter, args: List<Any?>): Double {
				return System.currentTimeMillis().toDouble()
			}

			override fun toString(): String {
				return "<native fn>"
			}
		})

		globals.define("print", object : LoxCallable {
			override fun arity() = 1

			override fun call(interpreter: Interpreter, args: List<Any?>) {
				var arg = args[0]
				if (args is Expr) arg = evaluate(arg as Expr)
				println(stringify(arg))
			}

			override fun toString(): String {
				return "<native fn>"
			}
		})

		globals.define("readLine", object : LoxCallable {
			override fun arity() = 0

			override fun call(interpreter: Interpreter, args: List<Any?>): String? {
				return readLine()
			}

			override fun toString(): String {
				return "<native fn>"
			}
		})
	}

	fun interpret(statements: List<Stmt>) {
		try {
			statements.forEach {
				execute(it)
			}
		} catch (error: RuntimeError) {
			Lox.runtimeError(error)
		}
	}

	fun interpretRepl(statements: List<Stmt>) {
		try {
			statements.forEach {
				if (it is Stmt.Expression) {
					println(stringify(evaluate(it.expression)))
				} else execute(it)
			}
		} catch (error: RuntimeError) {
			Lox.runtimeError(error)
		}
	}

	private fun execute(stmt: Stmt) {
		stmt.accept(this)
	}

	internal fun resolve(expr: Expr, depth: Int) {
		locals[expr] = depth
	}

	private fun evaluate(expr: Expr): Any? {
		return expr.accept(this)
	}

	private fun isTruthy(x: Any?): Boolean {
		return when(x) {
			null -> false
			is Boolean -> x
			else -> true
		}
	}

	private fun isEqual(x: Any?, y: Any?): Boolean {
		if (x == null && y == null) return true
		if (x == null) return false
		return x == y
	}

	private fun stringify(x: Any?): String {
		return when(x) {
			null -> "nil"
			is Double -> {
				var text = x.toString()
				if (text.endsWith(".0")) {
					text = text.substring(0, text.length - 2)
				}
				text
			}
			else -> x.toString()
		}
	}

	private fun binaryMaths(operator: Token, left: Any?, right: Any?): Any {
		if (left is Double && right is Double) {
			return when (operator.type) {
				MINUS -> left - right
				STAR -> left * right
				SLASH -> left / right
				GREATER -> left > right
				LESS -> left < right
				GREATER_EQUAL -> left >= right
				LESS_EQUAL -> left <= right
				else -> throw RuntimeError(operator, "Unexpected operator ${operator.lexeme}")
			}
		}
		throw RuntimeError(operator, "Operands must be numbers.")
	}

	fun executeBlock(stmt: List<Stmt>, newEnv: Environment) {
		val prevEnv = env
		env = newEnv
		try {
			stmt.forEach { execute(it) }
		} catch (exception: Break) {
			return
		} finally {
			this.env = prevEnv
		}
	}

	override fun visitLiteralExpr(expr: Expr.Literal): Any? {
		return expr.value
	}

	override fun visitVariableExpr(expr: Expr.Variable): Any? {
		return lookupVariable(expr.name, expr)
	}

	private fun lookupVariable(name: Token, expr: Expr): Any? {
		return when (val distance = locals[expr]) {
			null -> globals[name]
			else -> env.getAt(distance, name.lexeme)
		}
	}

	override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
		return evaluate(expr.expression)
	}

	override fun visitUnaryExpr(expr: Expr.Unary): Any? {
		val right = evaluate(expr.right)
		return when (expr.operator.type) {
			MINUS -> -(right as Double)
			BANG -> !isTruthy(right)
			else -> null
		}
	}

	override fun visitBinaryExpr(expr: Expr.Binary): Any? {
		val right = evaluate(expr.right)
		val left = evaluate(expr.left)
		return when (expr.operator.type) {
			PLUS -> {
				if (left is Double && right is Double) left + right
				else if (left is String) left + stringify(right)
				else if (right is String) stringify(left) + right
				else throw RuntimeError(expr.operator, "Operands must be either string or numbers.")
			}
			MINUS, STAR, SLASH, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL -> binaryMaths(expr.operator, left, right)
			EQUAL_EQUAL -> isEqual(left, right)
			else -> throw RuntimeError(expr.operator, "Illegal operator.")
		}
	}

	override fun visitLogicalExpr(expr: Expr.Logical): Boolean {
		val left = isTruthy(evaluate(expr.left))
		if (expr.operator.type == OR) {
			if (left) return true
		} else {
			if (!left) return false
		}
		return isTruthy(evaluate(expr.right))
	}

	override fun visitAssignExpr(expr: Expr.Assign): Any? {
		val value = evaluate(expr.value)

		val dist = locals[expr]
		if (dist == null) {
			globals.assign(expr.name, value)
		} else {
			env.assignAt(dist, expr.name, value)
		}

		return value
	}

	override fun visitCallExpr(expr: Expr.Call): Any? {
		val callee = evaluate(expr.callee)
		val args = ArrayList<Any?>()
		expr.args.forEach { args.add(evaluate(it)) }
		if (callee !is LoxCallable) {
			throw RuntimeError(
				expr.paren,
				"Can only call functions and classes"
			)
		}
		if (args.size != callee.arity()) {
			throw RuntimeError(
				expr.paren,
				"Expected ${callee.arity()} arguments but found ${args.size}"
			)
		}
		return callee.call(this, args)
	}

	override fun visitExpressionStmt(stmt: Stmt.Expression) {
		evaluate(stmt.expression)
	}

	override fun visitLetStmt(stmt: Stmt.Let) {
		val value = when(stmt.initializer) {
			null -> null
			else -> evaluate(stmt.initializer)
		}
		env.define(stmt.name.lexeme, value)
	}

	override fun visitIfStmt(stmt: Stmt.If) {
		try {
			if (isTruthy(evaluate(stmt.condition)))
				execute(stmt.thenBranch)
			else
				stmt.elseBranch?.let { execute(it) }
		} catch (exception: Break) {
			return
		}
	}

	override fun visitWhileStmt(stmt: Stmt.While) {
		while (isTruthy(evaluate(stmt.condition))) {
			try {
				execute(stmt.body)
			} catch (exception: Break) {
				return
			} catch (exception: Continue) {
				continue
			}
		}
	}

	override fun visitBlockStmt(stmt: Stmt.Block) {
		executeBlock(stmt.statements, Environment(env))
	}

	override fun visitFunctionStmt(stmt: Stmt.Function) {
		val loxFn = LoxFunction(stmt, env)
		env.define(stmt.name.lexeme, loxFn)
	}

	override fun visitClassStmt(stmt: Stmt.Class) {
		val name = stmt.name.lexeme
		env.define(name, null)

		val methods = HashMap<String, LoxFunction>()
		stmt.methods.forEach {
			val m = it.name.lexeme
			methods[m] = LoxFunction(it, env, m == "init", it.isStatic)
		}

		val klass = LoxClass(name, methods)
		env.assign(stmt.name, klass)
	}

	override fun visitGetExpr(expr: Expr.Get): Any? {
		val oobject = evaluate(expr.oobject)
		if (oobject is LoxInstance) return oobject.get(expr.name)
		else {
			throw RuntimeError(expr.name, "Only instances have properties.")
		}
	}

	override fun visitSetExpr(expr: Expr.Set): Any? {
		val oobject = evaluate(expr.oobject)
		val value = evaluate(expr.value)
		if (oobject is LoxInstance) oobject.set(expr.name, value)
		return value
	}

	override fun visitThisExpr(expr: Expr.This): Any? = lookupVariable(expr.keyword, expr)

	override fun visitReturnStmt(stmt: Stmt.Return) {
		val value = when(stmt.value) {
			null -> null
			else -> evaluate(stmt.value)
		}
		throw Return(value)
	}

	override fun visitBreakStmt(stmt: Stmt.Break) = throw Break()

	override fun visitContinueStmt(stmt: Stmt.Continue) = throw Continue()
}
