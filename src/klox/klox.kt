package klox

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
	when {
		args.size > 1 -> {
			println("Usage: klox <filepath>")
			exitProcess(64)
		}
		args.size == 1 -> Lox.runFile(args[0])
		else -> Lox.runPrompt()
	}
}

object Lox {
	private var hadError = false
	private var hadRuntimeError = false
	private var interpreter = Interpreter()

	fun runFile(path: String) {
		val start = System.currentTimeMillis();
		try {
			val bytes = Files.readAllBytes(Paths.get(path))
			val source = String(bytes, Charset.defaultCharset())
			run(source)
			if (hadRuntimeError) exitProcess(70)
		} catch (error: NoSuchFileException) {
			println("File not found: $path")
		}
		println("took ${System.currentTimeMillis() - start}ms")
	}

	fun runPrompt() {
		var inp: String?
		while (true) {
			System.out.print("> ")
			inp = readLine()
			if (inp == null) exitProcess(0)
			inp = inp.trim()
			if (inp.isEmpty()) continue
			if (inp[inp.length - 1] != ';') inp += ';'
			run(inp, true)
		}
	}

	fun error(token: Token, message: String) {
		if (token.type === TokenType.EOF) {
			report(token.line, " at end", message)
		} else {
			report(token.line, " at '${token.line}'", message)
		}
	}

	fun error(line: Int, message: String?) {
		report(line, "", message!!)
		hadError = true
	}

	fun runtimeError(error: RuntimeError) {
		System.err.println("[Line ${error.token.line}] ${error.message}")
		hadRuntimeError = true
	}

	private fun report(line: Int, where: String, message: String) {
		System.err.println("[line $line] Error$where: $message")
	}

	private fun run(source: String?, repl: Boolean = false) {
		hadError = false
		hadRuntimeError = false
		if (source == null) return
		val scanner = Scanner(source)
		val tokens = scanner.scanTokens()
		val parser = Parser(tokens)
		val statements = parser.parse()

		if (hadError) return

		if (statements == null) return

		val resolver = Resolver(interpreter)
		resolver.resolve(statements)

		if (hadError) return

		if (repl) interpreter.interpretRepl(statements)
		else interpreter.interpret(statements)
	}
}
