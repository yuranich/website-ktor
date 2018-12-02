package edu.yuranich

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import freemarker.cache.*
import io.ktor.freemarker.*
import io.ktor.content.*
import io.ktor.http.content.*
import io.ktor.sessions.*
import io.ktor.features.*
import io.ktor.auth.*
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val users = Collections.synchronizedMap(mutableMapOf<String, User>())

    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }

    install(PartialContent)

    install(Sessions) {
        cookie<MySession>("SESSION") {
            cookie.extensions["SameSite"] = "lax"
        }
    }

    install(Authentication) {
        form("login") {
            userParamName = "username"
            passwordParamName = "password"
            challenge = FormAuthChallenge.Unauthorized
            validate {
                credentials ->
                if (users[credentials.name]?.password == credentials.password) UserIdPrincipal(credentials.name)
                else null
            }
        }
    }

    routing {
        get("/") {
            val data = IndexData(listOf(1, 2, 3))
            call.respondHtml {
                head {
                    link(rel = "stylesheet", href = "/static/styles.css")
                }
                body {
                    img(src = "/static/ktor_logo.svg")
                    ul {
                        for (item in data.items) {
                            li { +"$item" }
                        }
                    }
                }
            }
        }

        route("/login") {
            get {
                call.respond(FreeMarkerContent("login.ftl", null))
            }
            authenticate ("login") {
                post {
                    val principal = call.principal<UserIdPrincipal>()
                    if (principal == null)
                        call.respond(FreeMarkerContent("login.ftl", mapOf("error" to "No principal")))
                    else {
                        call.sessions.set(MySession(principal.name))
                        call.respondRedirect("/", permanent = false)
                    }
                }
            }
        }

        route("/register") {
            get {
                call.respondHtml {
                    registerForm(null)
                }
            }
            post {
                val params = call.receiveParameters()
                val username = params["username"] ?: ""
                val password = params["password"] ?: ""
                val repeat = params["repeat"] ?: ""
                val email = params["email"] ?: ""
                if (checkValid(username, password, repeat, email, users)) {
                    users.put(username, User(username, password, email))
                    call.respondRedirect("login")
                } else {
                    call.respondHtml {
                        registerForm("Invalid input")
                    }
                }
            }
        }

        get("/html-dsl") {
            call.respondHtml {
                body {
                    h1 { +"HTML" }
                    ul {
                        for (n in 1..10) {
                            li { +"$n" }
                        }
                    }
                }
            }
        }

        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.red
                }
                p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }

        install(StatusPages) {
            exception<AuthenticationException> { cause ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { cause ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }
    }
}

data class User (val username: String, val password: String, val email: String)

private fun HTML.registerForm(error: String?) {
    head {
        link(rel = "stylesheet", href = "/static/styles.css")
    }
    body {
        form(action = "/register", method = FormMethod.post, encType = FormEncType.applicationXWwwFormUrlEncoded) {
            if (!error.isNullOrEmpty()) {
                p {
                    style { color = Color.red }
                    + error
                }
            }
            div {text("User:")}
            div {
                input(type = InputType.text, name = "username")
            }
            div { text("Password:") }
            div {
                input(type = InputType.password, name = "password")
            }
            div { text("Repeat password:") }
            div {
                input(type = InputType.password, name = "repeat")
            }
            div { text("Email:") }
            div {
                input(type = InputType.email, name = "email")
            }
            div {
                input(type = InputType.submit, name = "Register")
            }
        }
    }
}

fun checkValid(username: String, password: String, repeat: String, email: String, userStore: Map<String, User>): Boolean {
    return username.isNotEmpty() && password.isNotEmpty() && password == repeat && email.isNotEmpty() && userStore[username] == null
}

data class IndexData(val items: List<Int>)

data class MySession(val username: String)

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
