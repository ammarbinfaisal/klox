package klox

class Environment(private val enclosing: Environment? = null) {
	private val values = HashMap<String, Any?>()

	fun define(name: String, value: Any?) {
		values[name] = value
	}

	operator fun get(name: Token): Any? {
		return when {
			values.containsKey(name.lexeme) -> values[name.lexeme]
			enclosing != null -> enclosing[name]
			else -> throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
		}
	}

	fun getAt(dist: Int, name: String): Any? {
		return ancestor(dist).values[name]
	}

	fun assign(name: Token, value: Any?) {
		when {
			values.containsKey(name.lexeme) -> define(name.lexeme, value)
			enclosing != null -> enclosing.assign(name, value)
			else -> throw RuntimeError(name, "Undefined variable '" + name.lexeme + "'.")
		}
	}

	fun assignAt(dist: Int, name: Token, value: Any?) {
		ancestor(dist).values[name.lexeme] = value
	}

	private fun ancestor(dist: Int): Environment {
		var env = this
		var i = dist
		while (i --> 0) {
			env.enclosing?.let { env = it }
		}
		return env
	}
}
