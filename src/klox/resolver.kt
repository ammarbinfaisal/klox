package klox

import java.util.*
import kotlin.collections.HashMap

class Resolver(private val interpreter: Interpreter) : Expr.Visitor<Unit>,
	Stmt.Visitor<Unit> {
	private val scopes = Stack<HashMap<String, Boolean>>()

	private var currentClass = ClassType.NONE
	private var currentFunction = FunctionType.NONE

	private enum class FunctionType {
		NONE,
		FUNCTION,
		INITIALIZER,
		METHOD
	}

	private enum class ClassType {
		NONE,
		CLASS
	}

	fun resolve(stmts: List<Stmt>) {
		for (stmt in stmts) {
			resolve(stmt)
		}
	}

	private fun resolve(stmt: Stmt) {
		stmt.accept(this)
	}

	private fun resolve(expr: Expr) {
		expr.accept(this)
	}

	private fun beginScope() {
		scopes.push(HashMap())
	}

	private fun endScope() {
		scopes.pop()
	}

	private fun declare(name: Token) {
		if (scopes.isEmpty()) return
		val scope = scopes.peek()
		if (scope.containsKey(name.lexeme)) return Lox.error(name, "Already a variable with hisname in the scope")
		scope[name.lexeme] = false
	}

	private fun define(name: Token) {
		if (scopes.isEmpty()) return
		scopes.peek()[name.lexeme] = true
	}

	private fun resolveLocal(expr: Expr, name: Token) {
		for (i in scopes.indices.reversed()) {
			if (scopes[i].containsKey(name.lexeme)) {
				interpreter.resolve(expr, scopes.size - 1 - i)
				return
			}
		}
	}

	private fun resolveFunction(stmt: Stmt.Function, type: FunctionType) {
		val enclosingFunction = currentFunction
		currentFunction = type

		beginScope()
		stmt.params.forEach {
			declare(it)
			define(it)
		}
		resolve(stmt.body)
		endScope()

		currentFunction = enclosingFunction
	}

	override fun visitBlockStmt(stmt: Stmt.Block) {
		beginScope()
		resolve(stmt.statements)
		endScope()
	}

	override fun visitLetStmt(stmt: Stmt.Let) {
		declare(stmt.name)
		if (stmt.initializer != null) resolve(stmt.initializer)
		define(stmt.name)
	}

	override fun visitExpressionStmt(stmt: Stmt.Expression) {
		resolve(stmt.expression)
	}

	override fun visitVariableExpr(expr: Expr.Variable) {
		if (!scopes.isEmpty() && scopes.peek()[expr.name.lexeme] == false) {
			Lox.error(expr.name, "Can't read local variable in its own initializer.")
		}
		resolveLocal(expr, expr.name)
	}

	override fun visitAssignExpr(expr: Expr.Assign) {
		resolve(expr.value)
		resolveLocal(expr, expr.name)
	}

	override fun visitCallExpr(expr: Expr.Call) {
		resolve(expr.callee)
		expr.args.forEach { resolve(it) }
	}

	override fun visitBinaryExpr(expr: Expr.Binary) {
		resolve(expr.left)
		resolve(expr.right)
	}

	override fun visitGroupingExpr(expr: Expr.Grouping) {
		resolve(expr.expression)
	}

	override fun visitUnaryExpr(expr: Expr.Unary) {
		resolve(expr.right)
	}

	override fun visitLogicalExpr(expr: Expr.Logical) {
		resolve(expr.left)
		resolve(expr.right)
	}

	override fun visitIfStmt(stmt: Stmt.If) {
		resolve(stmt.condition)
		resolve(stmt.thenBranch)
		stmt.elseBranch?.let { resolve(it) }
	}

	override fun visitWhileStmt(stmt: Stmt.While) {
		resolve(stmt.condition)
		resolve(stmt.body)
	}

	override fun visitFunctionStmt(stmt: Stmt.Function) {
		declare(stmt.name)
		define(stmt.name)

		resolveFunction(stmt, FunctionType.FUNCTION)
	}

	override fun visitReturnStmt(stmt: Stmt.Return) {
		if (currentFunction == FunctionType.NONE)
			Lox.error(stmt.keyword, "Can't return from top level code")
		if (stmt.value != null) {
			if (currentFunction == FunctionType.INITIALIZER) {
				Lox.error(
					stmt.keyword,
					"Can't return a value from an initializer."
				)
			}
			resolve(stmt.value)
		}
	}

	override fun visitClassStmt(stmt: Stmt.Class) {
		declare(stmt.name)

		beginScope()
		scopes.peek()["this"] = true
		currentClass = ClassType.CLASS

		stmt.methods.forEach {
			val mType = when (it.name.lexeme) {
				"init" -> FunctionType.INITIALIZER
				else -> FunctionType.METHOD
			}
			resolveFunction(it, mType)
		}

		currentClass = ClassType.NONE
		endScope()
	}

	override fun visitThisExpr(expr: Expr.This) {
		if (currentClass == ClassType.NONE) {
			Lox.error(expr.keyword, "Can't use 'this' outside a class.")
		}
		resolveLocal(expr, expr.keyword)
	}

	override fun visitGetExpr(expr: Expr.Get) {
		resolve(expr.oobject)
	}

	override fun visitSetExpr(expr: Expr.Set) {
		resolve(expr.value)
		resolve(expr.oobject)
	}

	override fun visitBreakStmt(stmt: Stmt.Break) {}

	override fun visitContinueStmt(stmt: Stmt.Continue) {}

	override fun visitLiteralExpr(expr: Expr.Literal) {}
}
