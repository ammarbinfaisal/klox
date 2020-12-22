package klox

class Continue : Exception()

class Break : Exception()

class Return(val value: Any?): Exception()

class RuntimeError(val token: Token, message: String?) : RuntimeException(message)
