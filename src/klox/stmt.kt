//> Scanning scanner-class
package klox

abstract class Stmt {
    interface Visitor<R> {
        fun visitBlockStmt(stmt: Block): R
        fun visitClassStmt(stmt: Class): R
        fun visitExpressionStmt(stmt: Expression): R
        fun visitFunctionStmt(stmt: Function): R
        fun visitIfStmt(stmt: If): R
        fun visitReturnStmt(stmt: Return): R
        fun visitLetStmt(stmt: Let): R
        fun visitWhileStmt(stmt: While): R
        fun visitBreakStmt(stmt: Break): R
        fun visitContinueStmt(stmt: Continue): R
    }

    abstract fun <R> accept(stmtVisitor: Visitor<R>): R

    class Block(val statements: List<Stmt>) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitBlockStmt(this)
        }
    }

    class Class(
        val name: Token,
        val methods: List<Function>
    ) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitClassStmt(this)
        }
    }

    class Expression(val expression: Expr) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitExpressionStmt(this)
        }
    }

    class Function(
        val name: Token,
        val params: List<Token>,
        val body: List<Stmt>,
        val isStatic: Boolean = false
    ) :
        Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitFunctionStmt(this)
        }
    }

    class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitIfStmt(this)
        }
    }

    class Return(val keyword: Token, val value: Expr?) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitReturnStmt(this)
        }
    }

    class Let(val name: Token, val initializer: Expr?) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitLetStmt(this)
        }
    }

    class While(val condition: Expr, val body: Stmt) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitWhileStmt(this)
        }
    }

    class Break(val token: Token) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitBreakStmt(this)
        }
    }

    class Continue(val token: Token) : Stmt() {
        override fun <R> accept(stmtVisitor: Visitor<R>): R {
            return stmtVisitor.visitContinueStmt(this)
        }
    }
}
