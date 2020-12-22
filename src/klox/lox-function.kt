package klox

class LoxFunction(val declaration: Stmt.Function, private val closure: Environment, private val isInitializer: Boolean = false, val isStatic: Boolean = false): LoxCallable {
    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, args: List<Any?>): Any? {
        val env = Environment(closure)
        declaration.params.forEachIndexed { i, param ->
            env.define(param.lexeme, args[i])
        }

        try {
            interpreter.executeBlock(declaration.body, env)
        } catch(exception: Return) {
            return if(isInitializer) {
                closure.getAt(0, "this")
            } else exception.value
        }

        if(isInitializer) return closure.getAt(0, "this")

        return null
    }

    internal fun bind(instance: LoxInstance): LoxFunction {
        val env = Environment(closure)
        env.define("this", instance)
        return LoxFunction(declaration, env, isInitializer)
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme}>"
    }
}
