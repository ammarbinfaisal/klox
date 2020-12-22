package klox

class LoxClass(val name: String, private val methods: HashMap<String, LoxFunction>) : LoxCallable, LoxInstance(null) {
	private val initializer = findMethod("init")

	init {
		methods.values.forEach {
			if (it.isStatic) super.set(it.declaration.name, it)
		}
	}

	override fun arity(): Int = initializer?.arity() ?: 0

	override fun call(interpreter: Interpreter, args: List<Any?>): LoxInstance {
		val instance = LoxInstance(this)
		initializer?.bind(instance)?.call(interpreter, args)
		return instance
	}

	internal fun findMethod(name: String): LoxFunction? {
		return when {
			methods.containsKey(name) -> methods[name]
			else -> null
		}
	}

	override fun toString(): String = name
}
